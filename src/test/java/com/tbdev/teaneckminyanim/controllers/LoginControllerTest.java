package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginControllerTest {

    @Test
    void loginMethodsReturnsNeutralOptionsWithoutLookingUpIdentifier() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager@example.com"));

        assertTrue(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
        verifyNoInteractions(userService, passkeyCredentialService);
    }

    @Test
    void loginMethodsDoesNotExposeDisabledAccountState() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager"));

        assertTrue(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
        verifyNoInteractions(userService, passkeyCredentialService);
    }

    @Test
    void loginMethodsDoesNotQueryPasskeyStorage() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager"));

        assertTrue(response.passkeyAvailable());
        assertTrue(response.magicLinkAvailable());
        verifyNoInteractions(userService, passkeyCredentialService);
    }

    @Test
    void loginMethodsReflectsEnvironmentRollbackFlagsWithoutLookingUpAccount() {
        TNMUserService userService = mock(TNMUserService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        LoginController controller = controller(userService, passkeyCredentialService);
        ReflectionTestUtils.setField(controller, "passkeysEnabled", false);
        ReflectionTestUtils.setField(controller, "magicLinkEnabled", false);

        LoginController.LoginMethodsResponse response =
                controller.loginMethods(new LoginController.LoginIdentifierRequest("manager"));

        org.junit.jupiter.api.Assertions.assertFalse(response.passkeyAvailable());
        org.junit.jupiter.api.Assertions.assertFalse(response.magicLinkAvailable());
        verifyNoInteractions(userService, passkeyCredentialService);
    }

    @Test
    void magicLinkLoginRotatesExistingSessionId() {
        MagicLinkService magicLinkService = mock(MagicLinkService.class);
        PasskeyCredentialService passkeyCredentialService = mock(PasskeyCredentialService.class);
        TNMUser user = TNMUser.builder()
                .id("A1").username("manager").email("manager@example.com")
                .roleId(Role.ADMIN.getId()).organizationId("org").build();
        when(magicLinkService.consumeLoginToken("token")).thenReturn(user);
        when(passkeyCredentialService.hasPasskeys(user)).thenReturn(true);
        LoginController controller = new LoginController(mock(ApplicationSettingsService.class), magicLinkService,
                mock(TNMUserService.class), passkeyCredentialService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        String preLoginSessionId = request.getSession(true).getId();

        try {
            controller.verifyMagicLink("token", request);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertNotNull(request.getSession(false));
        assertNotEquals(preLoginSessionId, request.getSession(false).getId());
        assertNotNull(request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    private LoginController controller(TNMUserService userService,
                                       PasskeyCredentialService passkeyCredentialService) {
        return new LoginController(
                mock(ApplicationSettingsService.class),
                mock(MagicLinkService.class),
                userService,
                passkeyCredentialService);
    }
}
