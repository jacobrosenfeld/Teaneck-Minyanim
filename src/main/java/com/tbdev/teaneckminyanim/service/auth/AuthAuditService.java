package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.model.AuthAuditEvent;
import com.tbdev.teaneckminyanim.repo.AuthAuditEventRepository;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthAuditService {
    private final AuthAuditEventRepository repository;

    public void record(String eventType, String accountId, String ipHash, String details) {
        try {
            repository.save(AuthAuditEvent.builder()
                    .id(IDGenerator.generateID('E'))
                    .eventType(eventType)
                    .accountId(accountId)
                    .ipHash(ipHash)
                    .details(details == null ? null : details.substring(0, Math.min(details.length(), 1000)))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Could not persist auth audit event {} for account {}: {}",
                    eventType, accountId, e.getMessage());
        }
    }

    public static String hash(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
