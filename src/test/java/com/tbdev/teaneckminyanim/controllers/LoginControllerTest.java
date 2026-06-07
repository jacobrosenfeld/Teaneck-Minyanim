package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginControllerTest {

    @Test
    void loginMethodsReportsPasskeyWhenCredentialExistsForIdentifier() {
        TNMUserService userService = mock(TNMUserService.class);
        UserCredentialRepository credentialRepository = mock(UserCredentialRepository.class);
        LoginController controller = controller(userService, credentialRepository);
        TNMUser user = user(true);
        when(userService.findByIdentifier("manager@example.com")).thenReturn(user);
        Bytes userId = new Bytes("A1".getBytes(StandardCharsets.UTF_8));
        when(credentialRepository.findByUserId(userId)).thenReturn(List.of(mock(CredentialRecord.class)));

        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager@example.com"));

        assertTrue(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
        verify(credentialRepository).findByUserId(userId);
    }

    @Test
    void loginMethodsDoesNotExposeDisabledAccountPasskeys() {
        TNMUserService userService = mock(TNMUserService.class);
        UserCredentialRepository credentialRepository = mock(UserCredentialRepository.class);
        LoginController controller = controller(userService, credentialRepository);
        when(userService.findByIdentifier("manager")).thenReturn(user(false));

        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager"));

        assertFalse(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
    }

    private LoginController controller(TNMUserService userService,
                                       UserCredentialRepository credentialRepository) {
        return new LoginController(
                mock(ApplicationSettingsService.class),
                mock(MagicLinkService.class),
                userService,
                credentialRepository);
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
