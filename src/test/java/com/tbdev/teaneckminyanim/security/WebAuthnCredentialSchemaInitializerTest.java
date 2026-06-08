package com.tbdev.teaneckminyanim.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebAuthnCredentialSchemaInitializerTest {

    @Test
    void ensureSchemaDoesNotRunDdlWhenTableExists() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForObject(
                WebAuthnCredentialSchemaInitializer.TABLE_EXISTS_SQL, Integer.class))
                .thenReturn(0);
        WebAuthnCredentialSchemaInitializer initializer =
                new WebAuthnCredentialSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();
        initializer.ensureSchema();

        verify(jdbcOperations, times(1)).queryForObject(
                WebAuthnCredentialSchemaInitializer.TABLE_EXISTS_SQL, Integer.class);
        verify(jdbcOperations, never()).execute(anyString());
    }

    @Test
    void ensureSchemaCreatesMissingUserCredentialsTable() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForObject(
                WebAuthnCredentialSchemaInitializer.TABLE_EXISTS_SQL, Integer.class))
                .thenThrow(missingTableException());
        WebAuthnCredentialSchemaInitializer initializer =
                new WebAuthnCredentialSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();

        verify(jdbcOperations).execute(WebAuthnCredentialSchemaInitializer.CREATE_USER_CREDENTIALS_TABLE_SQL);
        assertTrue(WebAuthnCredentialSchemaInitializer.CREATE_USER_CREDENTIALS_TABLE_SQL
                .contains("credential_id VARBINARY(2048)"));
    }

    @Test
    void ensureSchemaRetriesAfterCreateFailure() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForObject(
                WebAuthnCredentialSchemaInitializer.TABLE_EXISTS_SQL, Integer.class))
                .thenThrow(missingTableException());
        doThrow(missingTableException())
                .doNothing()
                .when(jdbcOperations)
                .execute(eq(WebAuthnCredentialSchemaInitializer.CREATE_USER_CREDENTIALS_TABLE_SQL));
        WebAuthnCredentialSchemaInitializer initializer =
                new WebAuthnCredentialSchemaInitializer(jdbcOperations);

        assertThrows(BadSqlGrammarException.class, initializer::ensureSchema);
        initializer.ensureSchema();

        verify(jdbcOperations, times(2))
                .execute(WebAuthnCredentialSchemaInitializer.CREATE_USER_CREDENTIALS_TABLE_SQL);
    }

    private BadSqlGrammarException missingTableException() {
        return new BadSqlGrammarException(
                "query", "SELECT * FROM user_credentials", new SQLException("missing table"));
    }
}
