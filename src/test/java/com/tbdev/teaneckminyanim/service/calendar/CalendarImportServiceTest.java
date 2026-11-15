package com.tbdev.teaneckminyanim.service.calendar;

import com.sun.net.httpserver.HttpServer;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.LocationService;
import com.tbdev.teaneckminyanim.service.ZmanimHandler;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarImportServiceTest {

    private static final String ORG_ID = "org-import";
    private static final String SOURCE_DELETED_REASON =
            OrganizationCalendarEntry.SOURCE_DELETED_REASON;

    @Mock
    private CalendarUrlBuilder urlBuilder;

    @Mock
    private OrganizationCalendarEntryRepository entryRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private MinyanClassifier minyanClassifier;

    @Mock
    private CalendarMaterializationService materializationService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private LocationService locationService;

    private CalendarImportService service;
    private final List<HttpServer> servers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CalendarImportService(
                urlBuilder,
                new CalendarCsvParser(),
                entryRepository,
                organizationService,
                minyanClassifier,
                materializationService,
                locationService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        when(urlBuilder.isValidCalendarUrl(anyString())).thenReturn(true);
        when(organizationService.findById(ORG_ID)).thenReturn(Optional.of(organization()));
        lenient().when(minyanClassifier.classify(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(LocalDate.class),
                any(),
                anyCollection()))
                .thenReturn(new MinyanClassifier.ClassificationResult(
                        MinyanType.SHACHARIS,
                        "test classification"));
        lenient().when(minyanClassifier.normalizeTitle(nullable(String.class), any(MinyanType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        when(entryRepository.save(any(OrganizationCalendarEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void importCalendarForOrganization_createsNewEventAndSyncsCoverageWindow() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        when(urlBuilder.buildCsvExportUrl(anyString())).thenReturn(serveCsv(csv(date, "Shacharis", "Main")));
        when(entryRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(entryRepository.findByOrganizationIdAndDate(eq(ORG_ID), eq(date))).thenReturn(List.of());
        when(entryRepository.findEntriesInRange(ORG_ID, date, date)).thenReturn(List.of());

        CalendarImportService.ImportResult result = service.importCalendarForOrganization(ORG_ID);

        assertTrue(result.success);
        assertEquals(1, result.newEntries);
        assertEquals(0, result.updatedEntries);
        assertEquals(0, result.disabledMissingEntries);

        ArgumentCaptor<OrganizationCalendarEntry> savedCaptor =
                ArgumentCaptor.forClass(OrganizationCalendarEntry.class);
        verify(entryRepository).save(savedCaptor.capture());
        OrganizationCalendarEntry saved = savedCaptor.getValue();
        assertEquals(ORG_ID, saved.getOrganizationId());
        assertEquals(date, saved.getDate());
        assertEquals("Shacharis", saved.getTitle());
        assertTrue(saved.isEnabled());
        assertFalse(saved.isSourceDeleted());

        verify(materializationService).syncImportedEntriesInRangeLive(ORG_ID, date, date);
    }

    @Test
    void importCalendarForOrganization_updatesExistingEventAndClearsSourceDeletedState() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);
        OrganizationCalendarEntry existing = OrganizationCalendarEntry.builder()
                .id(10L)
                .organizationId(ORG_ID)
                .date(date)
                .title("Old Title")
                .fingerprint("will-be-replaced")
                .enabled(false)
                .sourceDeleted(true)
                .sourceDeletedAt(LocalDateTime.now().minusDays(1))
                .duplicateReason(SOURCE_DELETED_REASON)
                .build();

        when(locationService.findMatching(ORG_ID))
                .thenReturn(List.of(new Location("L-bm", "Beis Midrash", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString())).thenReturn(serveCsv(csv(date, "Shacharis", "Beis Midrash")));
        when(entryRepository.findByFingerprint(anyString()))
                .thenAnswer(invocation -> {
                    existing.setFingerprint(invocation.getArgument(0, String.class));
                    return Optional.of(existing);
                });
        when(entryRepository.findEntriesInRange(ORG_ID, date, date)).thenReturn(List.of(existing));

        CalendarImportService.ImportResult result = service.importCalendarForOrganization(ORG_ID);

        assertTrue(result.success);
        assertEquals(0, result.newEntries);
        assertEquals(1, result.updatedEntries);
        assertEquals(0, result.disabledMissingEntries);
        assertEquals("Shacharis", existing.getTitle());
        assertEquals("Beis Midrash", existing.getLocation());
        assertTrue(existing.isEnabled());
        assertFalse(existing.isSourceDeleted());
        assertNull(existing.getSourceDeletedAt());
        assertNull(existing.getDuplicateReason());

        verify(entryRepository).save(existing);
        verify(materializationService).syncImportedEntriesInRangeLive(ORG_ID, date, date);
    }

    @Test
    void importCalendarForOrganization_disablesEntriesMissingFromLatestCoveredImport() throws Exception {
        LocalDate date = LocalDate.now().plusDays(3);
        OrganizationCalendarEntry staleEntry = OrganizationCalendarEntry.builder()
                .id(20L)
                .organizationId(ORG_ID)
                .date(date)
                .startTime(java.time.LocalTime.of(7, 0))
                .title("Removed Shacharis")
                .fingerprint("stale-fingerprint")
                .enabled(true)
                .enabledManuallySet(false)
                .sourceDeleted(false)
                .build();

        when(urlBuilder.buildCsvExportUrl(anyString())).thenReturn(serveCsv(csv(date, "Mincha", "Main")));
        when(entryRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(entryRepository.findByOrganizationIdAndDate(eq(ORG_ID), eq(date))).thenReturn(List.of(staleEntry));
        when(entryRepository.findEntriesInRange(ORG_ID, date, date)).thenReturn(List.of(staleEntry));
        when(entryRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CalendarImportService.ImportResult result = service.importCalendarForOrganization(ORG_ID);

        assertTrue(result.success);
        assertEquals(1, result.newEntries);
        assertEquals(1, result.disabledMissingEntries);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<OrganizationCalendarEntry>> saveAllCaptor =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(entryRepository).saveAll(saveAllCaptor.capture());
        OrganizationCalendarEntry disabled = saveAllCaptor.getValue().iterator().next();
        assertEquals(staleEntry.getId(), disabled.getId());
        assertFalse(disabled.isEnabled());
        assertFalse(disabled.isEnabledManuallySet());
        assertTrue(disabled.isSourceDeleted());
        assertNotNull(disabled.getSourceDeletedAt());
        assertEquals(SOURCE_DELETED_REASON, disabled.getDuplicateReason());

        verify(materializationService).syncImportedEntriesInRangeLive(ORG_ID, date, date);
    }

    @Test
    void importCalendarForOrganization_marksMissingManuallyEnabledEntryWithoutDisablingIt() throws Exception {
        LocalDate date = LocalDate.now().plusDays(4);
        OrganizationCalendarEntry manuallyEnabledEntry = OrganizationCalendarEntry.builder()
                .id(30L)
                .organizationId(ORG_ID)
                .date(date)
                .startTime(java.time.LocalTime.of(8, 0))
                .title("Admin Restored Shacharis")
                .fingerprint("manual-enabled-fingerprint")
                .enabled(true)
                .enabledManuallySet(true)
                .sourceDeleted(false)
                .build();

        when(urlBuilder.buildCsvExportUrl(anyString())).thenReturn(serveCsv(csv(date, "Mincha", "Main")));
        when(entryRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(entryRepository.findByOrganizationIdAndDate(eq(ORG_ID), eq(date)))
                .thenReturn(List.of(manuallyEnabledEntry));
        when(entryRepository.findEntriesInRange(ORG_ID, date, date))
                .thenReturn(List.of(manuallyEnabledEntry));
        when(entryRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CalendarImportService.ImportResult result = service.importCalendarForOrganization(ORG_ID);

        assertTrue(result.success);
        assertEquals(1, result.newEntries);
        assertEquals(0, result.disabledMissingEntries);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<OrganizationCalendarEntry>> saveAllCaptor =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(entryRepository).saveAll(saveAllCaptor.capture());
        OrganizationCalendarEntry restored = saveAllCaptor.getValue().iterator().next();
        assertEquals(manuallyEnabledEntry.getId(), restored.getId());
        assertTrue(restored.isEnabled());
        assertTrue(restored.isEnabledManuallySet());
        assertTrue(restored.isSourceDeleted());
        assertNotNull(restored.getSourceDeletedAt());
        assertEquals(OrganizationCalendarEntry.SOURCE_RESTORED_REASON, restored.getDuplicateReason());

        verify(materializationService).syncImportedEntriesInRangeLive(ORG_ID, date, date);
    }

    // ---- Issue #225: location mapping and "Main" notes ----

    @Test
    void import_mainSanctuaryLocation_mapsToOrgLocationAndAddsNoMainNote() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(1);
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-main", "Main Sanctuary", ORG_ID),
                new Location("L-bm", "Beis Medrash", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Shacharis Main Sanctuary", "main sanctuary")));
        stubNewEntryLookups(date);

        service.importCalendarForOrganization(ORG_ID);

        OrganizationCalendarEntry saved = capturedSave();
        assertEquals(MinyanType.SHACHARIS, saved.getClassification());
        assertEquals("Main Sanctuary", saved.getLocation(), "location should use the org's Location name");
        assertNull(saved.getNotes(), "\"Main\" from the location name must not become a note");
    }

    @Test
    void import_unmatchedLocation_leavesNoLocationAndNoNotesFromIt() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(1);
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-hall", "Social Hall", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Mincha - Main Bais Medrash", "Main Bais Medrash")));
        stubNewEntryLookups(date);

        service.importCalendarForOrganization(ORG_ID);

        OrganizationCalendarEntry saved = capturedSave();
        assertEquals(MinyanType.MINCHA, saved.getClassification());
        assertNull(saved.getLocation(), "unmapped location text must not be shown as a location");
        assertNull(saved.getNotes(), "unmapped location text must not leak into notes");
        assertTrue(saved.getRawText().contains("Main Bais Medrash"), "raw import text is still kept for admins");
    }

    @Test
    void import_spellingVariantLocation_mapsToOrgLocation() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(1);
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-bm", "Beis Midrash", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Maariv", "Bais Medrash")));
        stubNewEntryLookups(date);

        service.importCalendarForOrganization(ORG_ID);

        assertEquals("Beis Midrash", capturedSave().getLocation());
    }

    @Test
    void import_locationOnlyInTitle_mapsToOrgLocation() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(1);
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-main", "Main Sanctuary", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Shacharis - Main Sanctuary", "")));
        stubNewEntryLookups(date);

        service.importCalendarForOrganization(ORG_ID);

        OrganizationCalendarEntry saved = capturedSave();
        assertEquals("Main Sanctuary", saved.getLocation());
        assertNull(saved.getNotes());
    }

    @Test
    void import_realQualifierStillBecomesNote() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(1);
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-main", "Main Sanctuary", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Teen Minyan", "Main Sanctuary")));
        stubNewEntryLookups(date);

        service.importCalendarForOrganization(ORG_ID);

        OrganizationCalendarEntry saved = capturedSave();
        assertEquals("Main Sanctuary", saved.getLocation());
        assertEquals("Teen Minyan", saved.getNotes());
    }

    @Test
    void reimport_fixesExistingRowWithRawLocationAndMainNote() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(2);
        OrganizationCalendarEntry existing = OrganizationCalendarEntry.builder()
                .id(40L)
                .organizationId(ORG_ID)
                .date(date)
                .title("Shacharis Main Sanctuary")
                .location("Upstairs Main Sanctuary")
                .notes("Main")
                .classification(MinyanType.SHACHARIS)
                .enabled(true)
                .build();
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-main", "Main Sanctuary", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Shacharis Main Sanctuary", "Upstairs Main Sanctuary")));
        stubExistingEntryLookups(existing, date);

        service.importCalendarForOrganization(ORG_ID);

        assertEquals("Main Sanctuary", existing.getLocation());
        assertNull(existing.getNotes(), "stale \"Main\" note must be cleared on re-import");
        verify(materializationService).syncImportedEntriesInRangeLive(ORG_ID, date, date);
    }

    @Test
    void reimport_clearsExistingUnmatchedLocation() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(2);
        OrganizationCalendarEntry existing = OrganizationCalendarEntry.builder()
                .id(41L)
                .organizationId(ORG_ID)
                .date(date)
                .title("Mincha")
                .location("Main Bais Medrash")
                .notes("Main")
                .classification(MinyanType.MINCHA)
                .enabled(true)
                .build();
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of());
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Mincha Main Bais Medrash", "Main Bais Medrash")));
        stubExistingEntryLookups(existing, date);

        service.importCalendarForOrganization(ORG_ID);

        assertNull(existing.getLocation());
        assertNull(existing.getNotes());
    }

    @Test
    void reimport_keepsManuallyEditedLocation() throws Exception {
        useRealClassifier();
        LocalDate date = LocalDate.now().plusDays(2);
        OrganizationCalendarEntry existing = OrganizationCalendarEntry.builder()
                .id(42L)
                .organizationId(ORG_ID)
                .date(date)
                .title("Shacharis")
                .location("Social Hall")
                .locationManuallyEdited(true)
                .classification(MinyanType.SHACHARIS)
                .enabled(true)
                .build();
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(
                new Location("L-main", "Main Sanctuary", ORG_ID),
                new Location("L-hall", "Social Hall", ORG_ID)));
        when(urlBuilder.buildCsvExportUrl(anyString()))
                .thenReturn(serveCsv(csv(date, "Shacharis Main Sanctuary", "Main Sanctuary")));
        stubExistingEntryLookups(existing, date);

        service.importCalendarForOrganization(ORG_ID);

        assertEquals("Social Hall", existing.getLocation(), "admin-chosen location must survive re-import");
        assertTrue(existing.isLocationManuallyEdited());
        assertNull(existing.getNotes());
    }

    private void useRealClassifier() {
        ApplicationSettingsService settings = new ApplicationSettingsService(null) {
            @Override
            public ZoneId getZoneId() {
                return ZoneId.of("America/New_York");
            }
        };
        service = new CalendarImportService(
                urlBuilder,
                new CalendarCsvParser(),
                entryRepository,
                organizationService,
                new MinyanClassifier(new ZmanimHandler(), settings),
                materializationService,
                locationService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private void stubNewEntryLookups(LocalDate date) {
        when(entryRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(entryRepository.findByOrganizationIdAndDate(eq(ORG_ID), eq(date))).thenReturn(List.of());
        when(entryRepository.findEntriesInRange(ORG_ID, date, date)).thenReturn(List.of());
    }

    private void stubExistingEntryLookups(OrganizationCalendarEntry existing, LocalDate date) {
        when(entryRepository.findByFingerprint(anyString()))
                .thenAnswer(invocation -> {
                    existing.setFingerprint(invocation.getArgument(0, String.class));
                    return Optional.of(existing);
                });
        when(entryRepository.findEntriesInRange(ORG_ID, date, date)).thenReturn(List.of(existing));
    }

    private OrganizationCalendarEntry capturedSave() {
        ArgumentCaptor<OrganizationCalendarEntry> captor = ArgumentCaptor.forClass(OrganizationCalendarEntry.class);
        verify(entryRepository).save(captor.capture());
        return captor.getValue();
    }

    private Organization organization() {
        return Organization.builder()
                .id(ORG_ID)
                .name("Import Org")
                .orgColor("#000000")
                .calendar("http://calendar.example/source")
                .build();
    }

    private String csv(LocalDate date, String name, String location) {
        return """
                Type,Start,End,Name,Location
                Event,%s 07:00:00,%s 07:30:00,%s,%s
                """.formatted(date, date, name, location);
    }

    private String serveCsv(String csv) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/calendar.csv", exchange -> {
            byte[] body = csv.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/csv; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/calendar.csv";
    }
}
