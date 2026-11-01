package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.sql.SQLSyntaxErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasskeyCredentialServiceTest {

    @Test
    void disabledAccountPasskeysCanStillBeCountedAndRevoked() {
        UserCredentialRepository repository = mock(UserCredentialRepository.class);
        PasskeyCredentialService service = new PasskeyCredentialService(repository);
        TNMUser user = TNMUser.builder().id("A1").enabled(false).build();
        var record = mock(org.springframework.security.web.webauthn.api.CredentialRecord.class);
        Bytes credentialId = new Bytes(new byte[]{1, 2});
        when(record.getCredentialId()).thenReturn(credentialId);
        when(repository.findByUserId(any())).thenReturn(java.util.List.of(record));
        assertEquals(1, service.countStoredPasskeys(user));
        service.revokeAll(user);
        org.mockito.Mockito.verify(repository).delete(credentialId);
    }

    @Test
    void missingCredentialTableIsTreatedAsNoPasskeys() {
        UserCredentialRepository credentialRepository = mock(UserCredentialRepository.class);
        PasskeyCredentialService service = new PasskeyCredentialService(credentialRepository);
        TNMUser user = TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .encryptedPassword("encrypted")
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
        when(credentialRepository.findByUserId(any(Bytes.class))).thenThrow(
                new BadSqlGrammarException("findByUserId",
                        "SELECT * FROM user_credentials",
                        new SQLSyntaxErrorException("Table 'user_credentials' doesn't exist")));

        assertEquals(0, service.countPasskeys(user));
        assertFalse(service.hasPasskeys(user));
    }
}
