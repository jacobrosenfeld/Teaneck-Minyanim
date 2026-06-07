package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;

@Repository
public class TNMUserWebAuthnUserEntityRepository implements PublicKeyCredentialUserEntityRepository {
    private final TNMUserService userService;

    public TNMUserWebAuthnUserEntityRepository(TNMUserService userService) {
        this.userService = userService;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        String accountId = new String(id.getBytes(), StandardCharsets.UTF_8);
        return fromUser(userService.findById(accountId));
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return fromUser(userService.findByName(username));
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        // ACCOUNT/TNMUser remains the source of truth for users.
    }

    @Override
    public void delete(Bytes id) {
        // User management is handled through ACCOUNT/TNMUser, not WebAuthn user entities.
    }

    private PublicKeyCredentialUserEntity fromUser(TNMUser user) {
        if (user == null || user.isDisabled()) {
            return null;
        }
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(user.getId().getBytes(StandardCharsets.UTF_8)))
                .name(user.getUsername())
                .displayName(user.getUsername())
                .build();
    }
}
