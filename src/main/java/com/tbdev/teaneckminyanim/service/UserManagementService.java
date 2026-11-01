package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final TNMUserService users;
    private final TNMUserRepository repository;
    private final OrganizationService organizations;

    public TNMUser requireSuperAdmin() {
        TNMUser actor = users.getCurrentUser();
        if (actor == null || actor.isDisabled() || !actor.isSuperAdmin()) {
            throw new AccessDeniedException("Only super admins can manage users and permissions.");
        }
        return actor;
    }

    public TNMUser requireTarget(String id) {
        requireSuperAdmin();
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    @Transactional
    public TNMUser create(String username, String email, String access, Set<String> organizationIds) {
        requireSuperAdmin();
        TNMUser target = TNMUser.builder().id(IDGenerator.generateID('A'))
                // Deliberately unusable by BCrypt; initial access is through an emailed link.
                .encryptedPassword("!PASSWORDLESS").build();
        updateProfile(target, username, email);
        assignAccess(target, access, organizationIds);
        return repository.save(target);
    }

    @Transactional
    public void update(String id, String email, String access, Set<String> organizationIds, boolean enabled) {
        TNMUser actor = requireSuperAdmin();
        TNMUser target = requireTarget(id);
        if (Objects.equals(actor.getId(), id) && (!enabled || !"SUPER_ADMIN".equals(access))) {
            throw new IllegalArgumentException("You cannot disable or remove your own super-admin access.");
        }
        updateProfile(target, target.getUsername(), email);
        assignAccess(target, access, organizationIds);
        target.setEnabled(enabled);
        repository.save(target);
    }

    private void updateProfile(TNMUser target, String username, String email) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = TNMUser.normalizeEmail(email);
        if (target.getUsername() == null && !name.matches("[a-z][a-z0-9_]{5,29}")) {
            throw new IllegalArgumentException("Username must be 6–30 letters, numbers or underscores, starting with a letter.");
        }
        if (normalizedEmail == null || normalizedEmail.length() > 254
                || !normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        boolean duplicate = repository.findAll().stream().anyMatch(other -> !Objects.equals(other.getId(), target.getId())
                && (name.equalsIgnoreCase(other.getUsername())
                    || normalizedEmail.equals(TNMUser.normalizeEmail(other.getEmail()))));
        if (duplicate) throw new IllegalArgumentException("That username or email already belongs to another account.");
        target.setUsername(name);
        target.setEmail(normalizedEmail);
        target.normalizeAuthFields();
    }

    private void assignAccess(TNMUser target, String access, Set<String> organizationIds) {
        if (!Set.of("SUPER_ADMIN", "MANAGER", "USER").contains(access == null ? "" : access)) {
            throw new IllegalArgumentException("Choose a valid role.");
        }
        Set<String> ids = organizationIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(organizationIds);
        if ("SUPER_ADMIN".equals(access)) {
            if (!ids.isEmpty()) throw new IllegalArgumentException("Super admins already have access to every shul; clear shul selections.");
            target.setRoleId(Role.ADMIN.getId());
            target.setOrganizationId(null);
            target.getAdditionalOrganizationIds().clear();
            return;
        }
        if (ids.isEmpty() || ids.stream().anyMatch(id -> id == null || organizations.findById(id).isEmpty())) {
            throw new IllegalArgumentException("Select at least one existing shul.");
        }
        String primary = ids.contains(target.getOrganizationId()) ? target.getOrganizationId() : ids.iterator().next();
        target.setRoleId("MANAGER".equals(access) ? Role.ADMIN.getId() : Role.USER.getId());
        target.setOrganizationId(primary);
        ids.remove(primary);
        target.getAdditionalOrganizationIds().clear();
        target.getAdditionalOrganizationIds().addAll(ids);
    }
}
