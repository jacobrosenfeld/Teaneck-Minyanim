package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShulProfileServiceTest {
    private OrganizationService organizationService;
    private TNMUserService userService;
    private GeocodingService geocodingService;
    private ShulProfileService service;
    private Organization org;
    private Organization otherOrg;

    private static ShulProfileCommand command() {
        return new ShulProfileCommand(null, null, null, null, null, null, null, null, null);
    }

    private static ShulProfileCommand withName(String name) {
        return new ShulProfileCommand(name, null, null, null, null, null, null, null, null);
    }

    private static ShulProfileCommand withSlug(String slug) {
        return new ShulProfileCommand(null, null, null, null, null, slug, null, null, null);
    }

    private static ShulProfileCommand withWebsite(String url) {
        return new ShulProfileCommand(null, null, url, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        organizationService = mock(OrganizationService.class);
        userService = mock(TNMUserService.class);
        geocodingService = mock(GeocodingService.class);
        service = new ShulProfileService(organizationService, userService, geocodingService);

        org = new Organization();
        org.setId("ORG-A");
        org.setName("Shul A");
        org.setUrlSlug("shul-a");
        org.setOrgColor("#275ed8");
        org.setNusach(Nusach.ASHKENAZ);
        org.setAddress("1 Old St");
        org.setLatitude(40.0);
        org.setLongitude(-74.0);
        otherOrg = new Organization();
        otherOrg.setId("ORG-B");
        otherOrg.setUrlSlug("shul-b");

        when(userService.canAccessOrganization("ORG-A")).thenReturn(true);
        when(organizationService.findById("ORG-A")).thenReturn(Optional.of(org));
        when(organizationService.findByUrlSlug("shul-b")).thenReturn(Optional.of(otherOrg));
        when(organizationService.findByUrlSlug("shul-a")).thenReturn(Optional.of(org));
        when(organizationService.update(any())).thenReturn(true);
    }

    @Test
    void updatesAllFieldsAndKeepsTheOnesNotSent() {
        ShulProfileCommand command = new ShulProfileCommand(" New Name ", "2 New St", "example.org", Nusach.SEFARD,
                "#112233", "New-Slug", "wa-link", "https://cal.example.org/c", true);

        service.update("ORG-A", command);

        assertEquals("New Name", org.getName());
        assertEquals("2 New St", org.getAddress());
        assertEquals("example.org", org.getWebsiteURIStr());
        assertEquals(Nusach.SEFARD, org.getNusach());
        assertEquals("#112233", org.getOrgColor());
        assertEquals("new-slug", org.getUrlSlug());
        assertEquals("wa-link", org.getWhatsapp());
        assertEquals("https://cal.example.org/c", org.getCalendar());
        assertTrue(org.getUseScrapedCalendar());
        verify(organizationService).ensureSlug(org);
        verify(organizationService).update(org);

        service.update("ORG-A", withName("Renamed"));
        assertEquals("Renamed", org.getName());
        assertEquals("2 New St", org.getAddress());
        assertEquals("new-slug", org.getUrlSlug());
        assertTrue(org.getUseScrapedCalendar());
    }

    @Test
    void blankOptionalFieldsAreCleared() {
        org.setWhatsapp("wa");
        org.setWebsiteURIStr("example.org");
        org.setCalendar("https://cal.example.org");

        service.update("ORG-A", new ShulProfileCommand(null, "", "", null, null, null, " ", "", null));

        assertNull(org.getAddress());
        assertNull(org.getWebsiteURIStr());
        assertNull(org.getWhatsapp());
        assertNull(org.getCalendar());
        assertEquals("Shul A", org.getName());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withName("")));
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withName("   ")));
        assertEquals("Shul A", org.getName());
        verify(organizationService, never()).update(any());
    }

    @Test
    void invalidWebsiteIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withWebsite("http://exa mple.org")));
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withWebsite("javascript:alert(1)")));
        assertNull(org.getWebsiteURIStr());
        verify(organizationService, never()).update(any());
        // A website without a scheme is the normal form on the shul page.
        service.update("ORG-A", withWebsite("kehilla.org"));
        assertEquals("kehilla.org", org.getWebsiteURIStr());
    }

    @Test
    void duplicateSlugIsRejectedButTheShulsOwnSlugIsFine() {
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withSlug("shul-b")));
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withSlug("SHUL-B")));
        assertEquals("shul-a", org.getUrlSlug());
        verify(organizationService, never()).update(any());

        service.update("ORG-A", withSlug("shul-a"));
        assertEquals("shul-a", org.getUrlSlug());
    }

    @Test
    void badSlugFormatIsRejected() {
        for (String slug : new String[]{"bad slug", "bad_slug", "slug!", "../x", "x".repeat(300)}) {
            assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", withSlug(slug)), slug);
        }
        assertEquals("shul-a", org.getUrlSlug());
    }

    @Test
    void blankSlugIsGeneratedFromTheName() {
        doAnswer(invocation -> {
            Organization target = invocation.getArgument(0);
            if (target.getUrlSlug() == null) {
                target.setUrlSlug("generated-slug");
            }
            return null;
        }).when(organizationService).ensureSlug(any());

        service.update("ORG-A", withSlug(" "));

        assertEquals("generated-slug", org.getUrlSlug());
    }

    @Test
    void badColorAndCalendarAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A",
                new ShulProfileCommand(null, null, null, null, "red", null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A",
                new ShulProfileCommand(null, null, null, null, null, null, null, "file:///etc/passwd", null)));
        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A",
                new ShulProfileCommand(null, null, null, null, null, null, null, "ftp://example.org/x", null)));
        assertEquals("#275ed8", org.getOrgColor());
        verify(organizationService, never()).update(any());
    }

    @Test
    void validationFailureLeavesTheShulUntouched() {
        ShulProfileCommand command = new ShulProfileCommand("Changed", "Changed St", null, null, null, "shul-b", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update("ORG-A", command));

        assertEquals("Shul A", org.getName());
        assertEquals("1 Old St", org.getAddress());
    }

    @Test
    void noFieldIsLimitedToSuperAdmins() {
        // Anyone with access to the shul may edit every field, as the shul page allows today.
        ShulProfileCommand all = new ShulProfileCommand("N", "A", "w.org", Nusach.ARIZAL, "#fff", "n-1", "wa",
                "https://c.example.org", true);

        service.update("ORG-A", all);

        verify(userService, never()).isSuperAdmin();
        assertEquals("n-1", org.getUrlSlug());
        assertEquals("https://c.example.org", org.getCalendar());
    }

    @Test
    void withoutAccessNothingHappens() {
        when(userService.canAccessOrganization("ORG-B")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.update("ORG-B", withName("X")));
        assertThrows(AccessDeniedException.class, () -> service.update(null, withName("X")));
        verifyNoInteractions(geocodingService);
        verify(organizationService, never()).findById(any());
        verify(organizationService, never()).update(any());
    }

    @Test
    void unknownShulIsNotFound() {
        when(userService.canAccessOrganization("GONE")).thenReturn(true);
        when(organizationService.findById("GONE")).thenReturn(Optional.empty());

        assertThrows(ShulProfileService.ShulNotFoundException.class, () -> service.update("GONE", withName("X")));
    }

    @Test
    void failedSaveIsReported() {
        when(organizationService.update(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.update("ORG-A", withName("X")));
    }

    @Test
    void geocodesOnlyWhenTheAddressChangesOrCoordinatesAreMissing() {
        when(geocodingService.geocodeAddress("2 New St")).thenReturn(new double[]{41.0, -73.0});

        service.update("ORG-A", withName("Renamed"));
        verifyNoInteractions(geocodingService);

        service.update("ORG-A", new ShulProfileCommand(null, "2 New St", null, null, null, null, null, null, null));
        verify(geocodingService).geocodeAddress("2 New St");
        verify(organizationService).updateGeocode(eq("ORG-A"), anyDouble(), anyDouble());
    }

    @Test
    void geocodingFailureDoesNotFailTheUpdate() {
        when(geocodingService.geocodeAddress(any())).thenThrow(new IllegalStateException("network"));

        service.update("ORG-A", new ShulProfileCommand(null, "3 Far St", null, null, null, null, null, null, null));

        assertEquals("3 Far St", org.getAddress());
        assertFalse(org.getLatitude() == null);
    }
}
