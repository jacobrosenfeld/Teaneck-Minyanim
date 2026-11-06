package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates an invite-ready super admin account on first startup if an explicit
 * bootstrap email is configured and no super admin exists.
 * Super admin = Role.ADMIN + no organizationId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminInitializerService {

    private final TNMUserService tnmUserService;

    @Value("${superadmin.username:admin}")
    private String defaultUsername;

    @Value("${superadmin.email:}")
    private String bootstrapEmail;

    @PostConstruct
    public void initializeSuperAdmin() {
        List<TNMUser> allUsers = tnmUserService.getAll();
        boolean superAdminExists = allUsers.stream().anyMatch(TNMUser::isSuperAdmin);

        if (superAdminExists) {
            log.debug("Super admin already exists — skipping auto-creation.");
            return;
        }

        String normalizedEmail = TNMUser.normalizeEmail(bootstrapEmail);
        if (!TNMUser.isValidAccountEmail(normalizedEmail)) {
            log.error("No super admin exists and SUPERADMIN_EMAIL is not configured with a valid address. "
                    + "Set it and restart to create the passwordless bootstrap account.");
            return;
        }

        log.info("No super admin found — creating passwordless bootstrap account '{}'.", defaultUsername);

        TNMUser superAdmin = TNMUser.builder()
                .id(IDGenerator.generateID('S'))
                .username(defaultUsername)
                .email(normalizedEmail)
                .encryptedPassword("!PASSWORDLESS")
                .organizationId(null)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();

        if (tnmUserService.save(superAdmin)) {
            log.info("Bootstrap super admin '{}' created. Request a magic link for {} to sign in.",
                    defaultUsername, normalizedEmail);
        } else {
            log.error("Failed to create default super admin account.");
        }
    }
}
