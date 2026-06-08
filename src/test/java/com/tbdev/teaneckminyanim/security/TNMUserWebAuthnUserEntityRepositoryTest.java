package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TNMUserWebAuthnUserEntityRepositoryTest {

    @Test
    void findByUsernameMapsEnabledTnmUserToWebAuthnEntity() {
        TNMUserService userService = mock(TNMUserService.class);
        when(userService.findByName("manager")).thenReturn(user(true));
        TNMUserWebAuthnUserEntityRepository repository =
                new TNMUserWebAuthnUserEntityRepository(userService);

        PublicKeyCredentialUserEntity entity = repository.findByUsername("manager");

        assertEquals("manager", entity.getName());
        assertEquals("manager", entity.getDisplayName());
        assertEquals("A1", new String(entity.getId().getBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void findByIdDoesNotExposeDisabledUsersForPasskeys() {
        TNMUserService userService = mock(TNMUserService.class);
        when(userService.findById("A1")).thenReturn(user(false));
        TNMUserWebAuthnUserEntityRepository repository =
                new TNMUserWebAuthnUserEntityRepository(userService);

        assertNull(repository.findById(new Bytes("A1".getBytes(StandardCharsets.UTF_8))));
    }

    private TNMUser user(boolean enabled) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(enabled)
                .build();
    }
}
