package com.tbdev.teaneckminyanim.service.calendar;

import com.sun.net.httpserver.HttpServer;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarImportServiceTest {

    private static final String ORG_ID = "org-import";
    private static final String SOURCE_DELETED_REASON =
            "Auto-disabled: Missing from latest source calendar import";

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
                materializationService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        when(urlBuilder.isValidCalendarUrl(anyString())).thenReturn(true);
        when(organizationService.findById(ORG_ID)).thenReturn(Optional.of(organization()));
        when(minyanClassifier.classify(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(LocalDate.class),
                any()))
                .thenReturn(new MinyanClassifier.ClassificationResult(
                        MinyanType.SHACHARIS,
                        "test classification"));
        when(minyanClassifier.normalizeTitle(nullable(String.class), any(MinyanType.class)))
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
                .enabledManuallySet(true)
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
