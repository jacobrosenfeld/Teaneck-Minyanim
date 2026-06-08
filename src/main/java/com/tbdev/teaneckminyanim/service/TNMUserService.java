package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TNMUserService {
    private final TNMUserRepository repository;

    @Autowired
    public TNMUserService(TNMUserRepository repository) {
        this.repository = repository;
    }

    public TNMUser findByName(String username) {
        return repository.findByUsername(username).orElse(null);
    }

    public TNMUser findByNormalizedEmail(String email) {
        String normalizedEmail = TNMUser.normalizeEmail(email);
        if (normalizedEmail == null) {
            return null;
        }
        return repository.findByEmailNormalized(normalizedEmail)
                .or(() -> repository.findByEmailIgnoreCase(normalizedEmail))
                .orElse(null);
    }

    public TNMUser findByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }

        String trimmedIdentifier = identifier.trim();
        if (trimmedIdentifier.isBlank()) {
            return null;
        }

        TNMUser user = findByName(trimmedIdentifier);
        if (user != null) {
            return user;
        }
        user = repository.findByUsernameIgnoreCase(trimmedIdentifier).orElse(null);
        if (user != null) {
            return user;
        }
        return findByNormalizedEmail(trimmedIdentifier);
    }

    public List<TNMUser> getAll() {
        return repository.findAll();
    }

    public TNMUser findById(String id) {
        return repository.findById(id).orElse(null);
    }

    public boolean save(TNMUser tnmUser) {
        try {
            prepareForPersistence(tnmUser);
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean update(TNMUser tnmUser) {
        try {
            prepareForPersistence(tnmUser);
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    public boolean delete(TNMUser tnmUser) {
        try {
            repository.delete(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Get the currently authenticated user
     */
    public TNMUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String username = authentication.getName();
        return findByName(username);
    }

    /**
     * Check if current user is a super admin (no organization, ADMIN role)
     */
    public boolean isSuperAdmin() {
        TNMUser user = getCurrentUser();
        return user != null && user.getOrganizationId() == null && user.role().equals(Role.ADMIN);
    }

    /**
     * Check if current user can access a specific organization
     * Super admins can access all orgs, regular users only their own
     */
    public boolean canAccessOrganization(String organizationId) {
        if (isSuperAdmin()) {
            return true;
        }
        TNMUser user = getCurrentUser();
        return user != null && organizationId.equals(user.getOrganizationId());
    }

    private void prepareForPersistence(TNMUser tnmUser) {
        if (tnmUser == null) {
            return;
        }
        tnmUser.normalizeAuthFields();
    }
}
