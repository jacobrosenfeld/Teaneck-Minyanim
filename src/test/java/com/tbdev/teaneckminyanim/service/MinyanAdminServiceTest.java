package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinyanAdminServiceTest {
    private MinyanService minyanService;
    private LocationService locationService;
    private TNMUserService userService;
    private CalendarMaterializationService materialization;
    private MinyanAdminService service;
    private Minyan minyan;

    @BeforeEach
    void setUp() {
        minyanService = mock(MinyanService.class);
        locationService = mock(LocationService.class);
        userService = mock(TNMUserService.class);
        materialization = mock(CalendarMaterializationService.class);
        service = new MinyanAdminService(minyanService, locationService, userService, materialization);

        minyan = new Minyan("M1", MinyanType.SHACHARIS, "LOC-A", "ORG-A", true,
                "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T8:30:0:0",
                "NM", "NM", "NM", "NM", "old notes", Nusach.ASHKENAZ, "#000000", "old-wa");
        when(userService.canAccessOrganization("ORG-A")).thenReturn(true);
        when(minyanService.findById("M1")).thenReturn(minyan);
        when(minyanService.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationService.findById("LOC-A")).thenReturn(new Location("LOC-A", "Main", "ORG-A"));
        when(locationService.findById("LOC-A2")).thenReturn(new Location("LOC-A2", "Second", "ORG-A"));
        when(locationService.findById("LOC-B")).thenReturn(new Location("LOC-B", "Other", "ORG-B"));
    }

    private static MinyanUpdateCommand details(Map<MinyanDay, MinyanTime> slots) {
        return new MinyanUpdateCommand(null, null, null, null, null, slots);
    }

    @Test
    void disableSavesThenSyncsRules() {
        Minyan result = service.setEnabled("ORG-A", "M1", false);

        assertFalse(result.isEnabled());
        InOrder order = inOrder(minyanService, materialization);
        order.verify(minyanService).update(minyan);
        order.verify(materialization).syncRulesForOrganizationLive("ORG-A");
    }

    @Test
    void enableSavesThenSyncsRules() {
        minyan.setEnabled(false);

        Minyan result = service.setEnabled("ORG-A", "M1", true);

        assertTrue(result.isEnabled());
        verify(minyanService).update(minyan);
        verify(materialization).syncRulesForOrganizationLive("ORG-A");
    }

    @Test
    void updateWithPartialSlotsKeepsTheOthers() {
        Map<MinyanDay, MinyanTime> slots = new EnumMap<>(MinyanDay.class);
        slots.put(MinyanDay.MONDAY, new MinyanTime("RNETZ:-5"));
        slots.put(MinyanDay.FRIDAY, null);

        service.update("ORG-A", "M1", details(slots));

        assertEquals("RNETZ:-5", minyan.getStartTime2());
        assertEquals("T7:0:0:0", minyan.getStartTime1());
        assertEquals("T7:0:0:0", minyan.getStartTime6());
        assertEquals("T8:30:0:0", minyan.getStartTime7());
        assertEquals("NM", minyan.getStartTimeRC());
        assertTrue(minyan.isEnabled());
        assertEquals("old notes", minyan.getNotes());
        verify(materialization).syncRulesForOrganizationLive("ORG-A");
    }

    @Test
    void updateChangesDetailsAndBlankClearsNotesAndWhatsapp() {
        service.update("ORG-A", "M1", new MinyanUpdateCommand(MinyanType.MAARIV, "LOC-A2", Nusach.SEFARD, "", "  ", null));

        assertEquals(MinyanType.MAARIV, minyan.getType());
        assertEquals("LOC-A2", minyan.getLocationId());
        assertEquals(Nusach.SEFARD, minyan.getNusach());
        assertNull(minyan.getNotes());
        assertNull(minyan.getWhatsapp());
    }

    @Test
    void applyCombinesEnabledAndUpdateWithOneSync() {
        Map<MinyanDay, MinyanTime> slots = new EnumMap<>(MinyanDay.class);
        slots.put(MinyanDay.SUNDAY, new MinyanTime("T9:15:0:0"));

        service.apply("ORG-A", "M1", false, details(slots));

        assertFalse(minyan.isEnabled());
        assertEquals("T9:15:0:0", minyan.getStartTime1());
        verify(minyanService).update(minyan);
        verify(materialization).syncRulesForOrganizationLive("ORG-A");
    }

    @Test
    void locationOfAnotherShulIsRejectedAndNothingChanges() {
        MinyanUpdateCommand command = new MinyanUpdateCommand(MinyanType.MAARIV, "LOC-B", null, "new notes", null, null);

        assertThrows(IllegalArgumentException.class, () -> service.apply("ORG-A", "M1", false, command));

        assertEquals("LOC-A", minyan.getLocationId());
        assertEquals(MinyanType.SHACHARIS, minyan.getType());
        assertEquals("old notes", minyan.getNotes());
        assertTrue(minyan.isEnabled());
        verify(minyanService, never()).update(any());
        verify(materialization, never()).syncRulesForOrganizationLive(any());
    }

    @Test
    void unknownOrBlankLocationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update("ORG-A", "M1", new MinyanUpdateCommand(null, "NOPE", null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.update("ORG-A", "M1", new MinyanUpdateCommand(null, " ", null, null, null, null)));
        verify(minyanService, never()).update(any());
    }

    @Test
    void overlongTextIsRejected() {
        String tooLong = "x".repeat(256);
        assertThrows(IllegalArgumentException.class,
                () -> service.update("ORG-A", "M1", new MinyanUpdateCommand(null, null, null, tooLong, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.update("ORG-A", "M1", new MinyanUpdateCommand(null, null, null, null, tooLong, null)));
        verify(minyanService, never()).update(any());
    }

    @Test
    void minyanOfAnotherShulIsNotFound() {
        Minyan other = new Minyan();
        other.setId("M-B");
        other.setOrganizationId("ORG-B");
        when(minyanService.findById("M-B")).thenReturn(other);

        assertThrows(MinyanAdminService.MinyanNotFoundException.class, () -> service.setEnabled("ORG-A", "M-B", false));
        verify(minyanService, never()).update(any());
        verify(materialization, never()).syncRulesForOrganizationLive(any());
    }

    @Test
    void missingMinyanIsNotFound() {
        assertThrows(MinyanAdminService.MinyanNotFoundException.class,
                () -> service.update("ORG-A", "NOPE", details(Map.of())));
    }

    @Test
    void withoutShulAccessNothingIsReadOrWritten() {
        when(userService.canAccessOrganization("ORG-B")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.setEnabled("ORG-B", "M1", false));
        assertThrows(AccessDeniedException.class, () -> service.update("ORG-B", "M1", details(Map.of())));
        assertThrows(AccessDeniedException.class, () -> service.get("ORG-B", "M1"));
        assertThrows(AccessDeniedException.class, () -> service.list("ORG-B"));
        assertThrows(AccessDeniedException.class, () -> service.setEnabled(null, "M1", false));
        verifyNoInteractions(materialization);
        verify(minyanService, never()).findById(any());
        verify(minyanService, never()).update(any());
    }
}
