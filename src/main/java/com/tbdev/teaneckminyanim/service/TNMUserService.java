package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    public List<TNMUser> getAll() {
        return repository.findAll();
    }

    public TNMUser findById(String id) {
        return repository.findById(id).orElse(null);
    }

    public boolean save(TNMUser tnmUser) {
        try {
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean update(TNMUser tnmUser) {
        try {
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
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
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
}

