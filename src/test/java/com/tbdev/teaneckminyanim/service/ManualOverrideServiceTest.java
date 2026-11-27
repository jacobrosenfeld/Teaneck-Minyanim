package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.ManualOverrideService.CreateOverrideCommand;
import com.tbdev.teaneckminyanim.service.ManualOverrideService.UpdateOverrideCommand;
import com.tbdev.teaneckminyanim.service.SuperAdminOverrideXlsxService.UpsertedManualEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualOverrideServiceTest {

    private static final String ORG = "ORGA";
    private static final LocalDate DAY = LocalDate.of(2026, 12, 14);
    private static final LocalTime TIME = LocalTime.of(18, 45);

    private final CalendarEventRepository repository = mock(CalendarEventRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final LocationService locations = mock(LocationService.class);
    private final TNMUserService users = mock(TNMUserService.class);
    private final SuperAdminOverrideXlsxService upserter = mock(SuperAdminOverrideXlsxService.class);
    private final ManualOverrideService service =
            new ManualOverrideService(repository, organizations, locations, users, upserter);

    @BeforeEach
    void setUp() {
        Organization org = new Organization();
        org.setId(ORG);
        org.setNusach(Nusach.SEFARD);
        when(organizations.findById(ORG)).thenReturn(Optional.of(org));
        when(users.canAccessOrganization(ORG)).thenReturn(true);
        when(repository.save(any(CalendarEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(upserter.upsertManualRow(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(call -> new UpsertedManualEvent(
                        event(100L + ((LocalDate) call.getArgument(1)).getDayOfMonth(), ORG, EventSource.MANUAL), false));
    }

    private static CreateOverrideCommand create(LocalDate date, LocalDate end) {
        return new CreateOverrideCommand(date, end, TIME, "MINCHA", null, null, null, null, null, null);
    }

    private static CalendarEvent event(Long id, String orgId, EventSource source) {
        return CalendarEvent.builder()
                .id(id).organizationId(orgId).date(DAY).startTime(TIME).minyanType(MinyanType.MINCHA)
                .source(source).sourceRef(source == EventSource.MANUAL ? "manual:ADDITIVE:x" : "ref").enabled(true)
                .build();
    }

    // --- create ---------------------------------------------------------------------------------------------

    @Test
    void rangeCreatesOneRowPerDayWithShulNusachAndAdditiveByDefault() {
        ManualOverrideService.CreateResult result = service.create(ORG, create(DAY, DAY.plusDays(4)), "alice");

        assertThat(result.events()).hasSize(5);
        assertThat(result.created()).isEqualTo(5);
        assertThat(result.updated()).isZero();
        for (int i = 0; i < 5; i++) {
            verify(upserter).upsertManualRow(eq(ORG), eq(DAY.plusDays(i)), eq(TIME), eq(MinyanType.MINCHA),
                    eq("ADDITIVE"), eq(null), eq(null), eq(null), eq(Nusach.SEFARD), eq(true), eq("alice"));
        }
    }

    @Test
    void endDateDefaultsToStartDateAndFullDayReplaceIsPassedThrough() {
        CreateOverrideCommand cmd = new CreateOverrideCommand(DAY, null, TIME, "SHACHARIS", "full_day_replace",
                null, " Main hall ", "  Fast day  ", "ashkenaz", false);

        assertThat(service.create(ORG, cmd, "alice").events()).hasSize(1);

        verify(upserter).upsertManualRow(ORG, DAY, TIME, MinyanType.SHACHARIS, "FULL_DAY_REPLACE",
                null, "Main hall", "Fast day", Nusach.ASHKENAZ, false, "alice");
    }

    @Test
    void sixtyTwoDaysIsAllowedAndSixtyThreeIsNot() {
        assertThat(service.create(ORG, create(DAY, DAY.plusDays(61)), "a").events()).hasSize(62);

        assertThatThrownBy(() -> service.create(ORG, create(DAY, DAY.plusDays(62)), "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("62");
    }

    @Test
    void invalidInputIsRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.create(ORG, create(DAY, DAY.minusDays(1)), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("End date cannot be before start date");
        assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(DAY, null, TIME, "BOGUS",
                null, null, null, null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid minyan type");
        assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(DAY, null, TIME, "MINCHA",
                null, null, null, null, "NOPE", null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid nusach");
        assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(DAY, null, TIME, "MINCHA",
                "SIDEWAYS", null, null, null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("override mode");
        assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(null, null, TIME, "MINCHA",
                null, null, null, null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Date");
        assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(DAY, null, null, "MINCHA",
                null, null, null, null, null, null), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("time");
        verify(upserter, never()).upsertManualRow(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any());
    }

    @Test
    void locationOfAnotherShulOrUnknownLocationIsRejected() {
        when(locations.findById("LOTHER")).thenReturn(new Location("Other", "ORGB"));
        when(locations.findById("LMISSING")).thenReturn(null);

        for (String id : List.of("LOTHER", "LMISSING")) {
            assertThatThrownBy(() -> service.create(ORG, new CreateOverrideCommand(DAY, null, TIME, "MINCHA",
                    null, id, null, null, null, null), "a"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid location for this shul");
        }
    }

    @Test
    void chosenLocationSuppliesItsNameUnlessOneIsGiven() {
        Location hall = new Location("Social hall", ORG);
        when(locations.findById(hall.getId())).thenReturn(hall);

        service.create(ORG, new CreateOverrideCommand(DAY, null, TIME, "MINCHA", null, hall.getId(), null, null,
                null, null), "a");

        verify(upserter).upsertManualRow(eq(ORG), eq(DAY), eq(TIME), eq(MinyanType.MINCHA), eq("ADDITIVE"),
                eq(hall.getId()), eq("Social hall"), eq(null), eq(Nusach.SEFARD), eq(true), eq("a"));
    }

    @Test
    void createWithoutAccessIsDeniedAndUnknownShulIsNotFound() {
        when(users.canAccessOrganization("ORGZ")).thenReturn(false);
        assertThatThrownBy(() -> service.create("ORGZ", create(DAY, null), "a"))
                .isInstanceOf(AccessDeniedException.class);

        when(users.canAccessOrganization("GONE")).thenReturn(true);
        when(organizations.findById("GONE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create("GONE", create(DAY, null), "a"))
                .isInstanceOf(OverrideNotFoundException.class);
        verify(upserter, never()).upsertManualRow(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any());
    }

    // --- update ---------------------------------------------------------------------------------------------

    @Test
    void updateChangesOnlyTheGivenFieldsAndStampsTheEditor() {
        CalendarEvent row = event(5L, ORG, EventSource.MANUAL);
        row.setNotes("old");
        row.setLocationName("Hall");
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        CalendarEvent saved = service.update(ORG, "5",
                new UpdateOverrideCommand(LocalTime.of(19, 0), "FULL_DAY_REPLACE", null, null, null, "edot_hamizrach",
                        false), "bob");

        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(saved.getNotes()).isEqualTo("old");
        assertThat(saved.getLocationName()).isEqualTo("Hall");
        assertThat(saved.getNusach()).isEqualTo(Nusach.EDOT_HAMIZRACH);
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getSourceRef()).startsWith(EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX + ":");
        assertThat(ManualOverrideService.modeOf(saved)).isEqualTo("FULL_DAY_REPLACE");
        assertThat(saved.getEditedBy()).isEqualTo("bob");
        assertThat(saved.isManuallyEdited()).isTrue();
        assertThat(saved.getEditedAt()).isNotNull();
    }

    @Test
    void blankValuesClearNotesAndLocation() {
        CalendarEvent row = event(5L, ORG, EventSource.MANUAL);
        row.setNotes("old");
        row.setLocationId("L1");
        row.setLocationName("Hall");
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        CalendarEvent saved = service.update(ORG, "5",
                new UpdateOverrideCommand(null, null, " ", "", null, null, null), "bob");

        assertThat(saved.getNotes()).isNull();
        assertThat(saved.getLocationId()).isNull();
        assertThat(saved.getLocationName()).isNull();
    }

    @Test
    void updateRejectsBadValuesAndForeignLocations() {
        CalendarEvent row = event(5L, ORG, EventSource.MANUAL);
        when(repository.findById(5L)).thenReturn(Optional.of(row));
        when(locations.findById("LOTHER")).thenReturn(new Location("Other", "ORGB"));

        assertThatThrownBy(() -> service.update(ORG, "5",
                new UpdateOverrideCommand(null, "SIDEWAYS", null, null, null, null, null), "b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(ORG, "5",
                new UpdateOverrideCommand(null, null, null, "LOTHER", null, null, null), "b"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid location for this shul");
        assertThatThrownBy(() -> service.update(ORG, "5",
                new UpdateOverrideCommand(null, null, null, null, null, "NOPE", null), "b"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid nusach");
        verify(repository, never()).save(any());
    }

    // --- RULES / IMPORTED rows, other shuls, access ---------------------------------------------------------

    @Test
    void nonManualRowsCannotBeUpdatedToggledOrDeleted() {
        for (EventSource source : List.of(EventSource.RULES, EventSource.IMPORTED)) {
            when(repository.findById(7L)).thenReturn(Optional.of(event(7L, ORG, source)));

            assertThatThrownBy(() -> service.update(ORG, "7",
                    new UpdateOverrideCommand(null, null, "x", null, null, null, null), "b"))
                    .isInstanceOf(NotManualOverrideException.class);
            assertThatThrownBy(() -> service.setEnabled(ORG, "7", false, "b"))
                    .isInstanceOf(NotManualOverrideException.class);
            assertThatThrownBy(() -> service.toggle(ORG, "7", "b")).isInstanceOf(NotManualOverrideException.class);
            assertThatThrownBy(() -> service.delete(ORG, "7")).isInstanceOf(NotManualOverrideException.class);
        }
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void anotherShulsEventMissingEventAndBadIdAreNotFound() {
        when(repository.findById(8L)).thenReturn(Optional.of(event(8L, "ORGB", EventSource.MANUAL)));
        when(repository.findById(9L)).thenReturn(Optional.empty());

        for (String id : List.of("8", "9", "abc", "")) {
            assertThatThrownBy(() -> service.delete(ORG, id)).isInstanceOf(OverrideNotFoundException.class);
            assertThatThrownBy(() -> service.toggle(ORG, id, "b")).isInstanceOf(OverrideNotFoundException.class);
            assertThatThrownBy(() -> service.setEnabled(ORG, id, true, "b"))
                    .isInstanceOf(OverrideNotFoundException.class);
            assertThatThrownBy(() -> service.update(ORG, id,
                    new UpdateOverrideCommand(null, null, "x", null, null, null, null), "b"))
                    .isInstanceOf(OverrideNotFoundException.class);
        }
        verify(repository, never()).delete(any());
    }

    @Test
    void everyActionRequiresAccessToTheShul() {
        when(users.canAccessOrganization("ORGZ")).thenReturn(false);

        assertThatThrownBy(() -> service.update("ORGZ", "1",
                new UpdateOverrideCommand(null, null, "x", null, null, null, null), "b"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setEnabled("ORGZ", "1", true, "b")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.toggle("ORGZ", "1", "b")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete("ORGZ", "1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list("ORGZ", DAY, DAY)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findById(any());
    }

    // --- toggle / delete / list ---------------------------------------------------------------------------

    @Test
    void toggleFlipsAndSetEnabledSets() {
        CalendarEvent row = event(5L, ORG, EventSource.MANUAL);
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        assertThat(service.toggle(ORG, "5", "bob").isEnabled()).isFalse();
        assertThat(service.toggle(ORG, "5", "bob").isEnabled()).isTrue();
        assertThat(service.setEnabled(ORG, "5", false, "bob").isEnabled()).isFalse();
        assertThat(row.getEditedBy()).isEqualTo("bob");
    }

    @Test
    void deleteRemovesTheRow() {
        CalendarEvent row = event(5L, ORG, EventSource.MANUAL);
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        service.delete(ORG, "5");

        verify(repository).delete(row);
    }

    @Test
    void superAdminVariantsNeedSuperAdminAndSkipTheShulCheck() {
        CalendarEvent row = event(5L, "ORGB", EventSource.MANUAL);
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        when(users.isSuperAdmin()).thenReturn(false);
        assertThatThrownBy(() -> service.deleteAsSuperAdmin(5L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.toggleAsSuperAdmin(5L, "s")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createAsSuperAdmin(ORG, create(DAY, null), "s"))
                .isInstanceOf(AccessDeniedException.class);

        when(users.isSuperAdmin()).thenReturn(true);
        assertThat(service.toggleAsSuperAdmin(5L, "s").isEnabled()).isFalse();
        service.deleteAsSuperAdmin(5L);
        verify(repository).delete(row);

        when(repository.findById(6L)).thenReturn(Optional.of(event(6L, ORG, EventSource.RULES)));
        assertThatThrownBy(() -> service.deleteAsSuperAdmin(6L)).isInstanceOf(NotManualOverrideException.class);
        assertThatThrownBy(() -> service.deleteAsSuperAdmin(404L)).isInstanceOf(OverrideNotFoundException.class);
    }

    @Test
    void listIsSortedAndCapped() {
        CalendarEvent late = event(2L, ORG, EventSource.MANUAL);
        late.setStartTime(LocalTime.of(20, 0));
        CalendarEvent early = event(1L, ORG, EventSource.MANUAL);
        when(repository.findByOrganizationIdAndSourceAndDateBetween(ORG, EventSource.MANUAL, DAY, DAY.plusDays(61)))
                .thenReturn(List.of(late, early));

        assertThat(service.list(ORG, DAY, DAY.plusDays(61))).containsExactly(early, late);
        assertThatThrownBy(() -> service.list(ORG, DAY, DAY.plusDays(62)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(ORG, DAY.plusDays(1), DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(ORG, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modeIsDecodedFromSourceRef() {
        CalendarEvent row = event(1L, ORG, EventSource.MANUAL);
        assertThat(ManualOverrideService.modeOf(row)).isEqualTo("ADDITIVE");
        row.setSourceRef("manual:FULL_DAY_REPLACE:abc");
        assertThat(ManualOverrideService.modeOf(row)).isEqualTo("FULL_DAY_REPLACE");
        row.setSourceRef(null);
        assertThat(ManualOverrideService.modeOf(row)).isEqualTo("ADDITIVE");
    }
}
