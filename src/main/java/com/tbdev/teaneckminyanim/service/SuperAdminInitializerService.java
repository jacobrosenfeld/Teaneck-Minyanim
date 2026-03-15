package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.security.Encrypter;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates a default super admin account on first startup if none exists.
 * Super admin = Role.ADMIN + no organizationId.
 * Change the password via the admin UI after first login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminInitializerService {

    private final TNMUserService tnmUserService;

    @Value("${superadmin.username:admin}")
    private String defaultUsername;

    @Value("${superadmin.password:Admin123!}")
    private String defaultPassword;

    @PostConstruct
    public void initializeSuperAdmin() {
        List<TNMUser> allUsers = tnmUserService.getAll();
        boolean superAdminExists = allUsers.stream().anyMatch(TNMUser::isSuperAdmin);

        if (superAdminExists) {
            log.debug("Super admin already exists — skipping auto-creation.");
            return;
        }

        log.info("No super admin found — creating default super admin account '{}'.", defaultUsername);

        TNMUser superAdmin = TNMUser.builder()
                .id(IDGenerator.generateID('S'))
                .username(defaultUsername)
                .email("admin@localhost")
                .encryptedPassword(Encrypter.encrytedPassword(defaultPassword))
                .organizationId(null)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();

        if (tnmUserService.save(superAdmin)) {
            log.info("Default super admin '{}' created. Please change the password after first login.", defaultUsername);
        } else {
            log.error("Failed to create default super admin account.");
        }
    }
}
