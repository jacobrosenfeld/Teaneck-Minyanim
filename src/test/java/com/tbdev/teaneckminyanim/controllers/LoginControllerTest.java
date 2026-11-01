package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private LoginController controller(TNMUserService userService,
                                       PasskeyCredentialService passkeyCredentialService) {
        return new LoginController(
                mock(ApplicationSettingsService.class),
                mock(MagicLinkService.class),
                userService,
                passkeyCredentialService);
    }
}
