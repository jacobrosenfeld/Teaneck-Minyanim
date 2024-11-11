package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.structure.TNMUser;
import com.tbdev.teaneckminyanim.structure.global.Nusach;
import com.tbdev.teaneckminyanim.structure.model.Account;
import com.tbdev.teaneckminyanim.structure.model.Organization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization findByName(String name) {
        return organizationRepository.findByName(name).orElse(null);
    }

    public Organization findById(String id) {
        Optional<Organization> organizationById = organizationRepository.findById(id);
        if (organizationById.isEmpty()){
            return new Organization();
        }
        organizationById.get().setNusach(Nusach.fromString(organizationById.get().getNusachStr()));
        return organizationById.get();
    }

    public List<Organization> getAll() {
        return organizationRepository.findAll();
    }

    public boolean save(Organization organization) {
        try {
            organizationRepository.save(organization);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean delete(Organization organization) {
        try {
            organizationRepository.delete(organization);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean update(Organization organization) {
        try {
            organizationRepository.save(organization);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public List<TNMUser> getUsersForOrganization(Organization organization) {
        List<TNMUser> users = new ArrayList<>();
        List<Account> accountsByOrganizationId = organizationRepository.findAccountsByOrganizationId(organization.getId());
        for (Account account : accountsByOrganizationId) {
           users.add(TNMUser.builder()
                   .id(account.getId())
                   .encryptedPassword(account.getEncryptedPassword())
                   .organizationId(account.getOrganizationId())
                   .roleId(Integer.parseInt(account.getRoleId()))
                   .build());

        }
        return users;
    }
}
