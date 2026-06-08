package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginControllerTest {

    @Test
    void loginMethodsReportsPasskeyWhenCredentialExistsForIdentifier() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        TNMUser user = user(true);
        when(userService.findByIdentifier("manager@example.com")).thenReturn(user);
        when(passkeyCredentialService.hasPasskeys(user)).thenReturn(true);

        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager@example.com"));

        assertTrue(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
    }

    @Test
    void loginMethodsDoesNotExposeDisabledAccountPasskeys() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        when(userService.findByIdentifier("manager")).thenReturn(user(false));

        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager"));

        assertFalse(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
    }

    private LoginController controller(TNMUserService userService,
                                       PasskeyCredentialService passkeyCredentialService) {
        return new LoginController(
                mock(ApplicationSettingsService.class),
                mock(MagicLinkService.class),
                userService,
                passkeyCredentialService);
    }

    private TNMUser user(boolean enabled) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .emailNormalized("manager@example.com")
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(enabled)
                .build();
    }
}
