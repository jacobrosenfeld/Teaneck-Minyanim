package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.model.TNMUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class PasskeyCredentialService {
    private final UserCredentialRepository userCredentialRepository;

    public PasskeyCredentialService(UserCredentialRepository userCredentialRepository) {
        this.userCredentialRepository = userCredentialRepository;
    }

    public boolean hasPasskeys(TNMUser user) {
        return countPasskeys(user) > 0;
    }

    public int countPasskeys(TNMUser user) {
        return findPasskeys(user).size();
    }

    public List<?> findPasskeys(TNMUser user) {
        if (user == null || user.getId() == null || user.isDisabled()) {
            return Collections.emptyList();
        }
        try {
            List<?> credentials = userCredentialRepository.findByUserId(
                    new Bytes(user.getId().getBytes(StandardCharsets.UTF_8)));
            return credentials == null ? Collections.emptyList() : credentials;
        } catch (DataAccessException e) {
            log.warn("Could not load passkey credentials for account {}; treating as no passkeys. "
                    + "Run the auth foundation migration if user_credentials is missing: {}",
                    user.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    public String setupPromptUrl(TNMUser user) {
        String accountId = user == null ? "" : user.getId();
        return UriComponentsBuilder
                .fromPath("/admin/account")
                .queryParam("id", accountId)
                .queryParam("setupPasskey", "true")
                .toUriString();
    }
}
