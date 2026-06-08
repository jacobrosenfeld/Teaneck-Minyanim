package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyControllerTest {

    @Test
    void passkeysPageRendersWithJavaTimeDatePattern() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        VersionService versionService = mock(VersionService.class);
        PasskeyController controller = new PasskeyController(
                userService,
                passkeyCredentialService,
                settingsService,
                versionService,
                mock(OrganizationService.class));
        TNMUser user = user();
        when(userService.getCurrentUser()).thenReturn(user);
        when(passkeyCredentialService.findPasskeys(user)).thenReturn(List.of());
        when(settingsService.getTimeZone()).thenReturn(TimeZone.getTimeZone("America/New_York"));
        when(versionService.getVersion()).thenReturn("test-version");

        ModelAndView mv = controller.passkeys();

        assertEquals("admin/passkeys", mv.getViewName());
        assertEquals(0, mv.getModel().get("passkeyCount"));
        assertNotNull(mv.getModel().get("date"));
        verify(passkeyCredentialService, never()).countPasskeys(user);
    }

    private TNMUser user() {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
    }
}
