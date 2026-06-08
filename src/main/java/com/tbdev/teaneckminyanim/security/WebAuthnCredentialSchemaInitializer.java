package com.tbdev.teaneckminyanim.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class WebAuthnCredentialSchemaInitializer {
    static final String TABLE_EXISTS_SQL = "SELECT COUNT(*) FROM user_credentials WHERE 1 = 0";
    static final String CREATE_USER_CREDENTIALS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS user_credentials (
                credential_id VARBINARY(2048) NOT NULL,
                user_entity_user_id VARCHAR(1000) NOT NULL,
                public_key BLOB NOT NULL,
                signature_count BIGINT,
                uv_initialized BOOLEAN,
                backup_eligible BOOLEAN NOT NULL,
                authenticator_transports VARCHAR(1000),
                public_key_credential_type VARCHAR(100),
                backup_state BOOLEAN NOT NULL,
                attestation_object BLOB,
                attestation_client_data_json BLOB,
                created TIMESTAMP,
                last_used TIMESTAMP,
                label VARCHAR(1000) NOT NULL,
                PRIMARY KEY (credential_id)
            )
            """;

    private final JdbcOperations jdbcOperations;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public WebAuthnCredentialSchemaInitializer(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @PostConstruct
    public void initialize() {
        ensureSchema();
    }

    public void ensureSchema() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            if (tableExists()) {
                return;
            }
            jdbcOperations.execute(CREATE_USER_CREDENTIALS_TABLE_SQL);
            log.info("Created missing WebAuthn user_credentials table");
        } catch (DataAccessException e) {
            initialized.set(false);
            log.error("Could not initialize WebAuthn credential storage. "
                    + "Passkey registration and sign-in require the user_credentials table.", e);
            throw e;
        }
    }

    private boolean tableExists() {
        try {
            jdbcOperations.queryForObject(TABLE_EXISTS_SQL, Integer.class);
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }
}
