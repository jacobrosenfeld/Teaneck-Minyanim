package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.model.Account;
import com.tbdev.teaneckminyanim.model.Organization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
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
        Optional<Organization> organizationByName = organizationRepository.findByName(name);
        if (organizationByName.isEmpty()){
            return new Organization();
        }
        organizationByName.get().setNusach(Nusach.fromString(organizationByName.get().getNusachStr()));
        return organizationByName.get();
    }

    public Optional<Organization> findById(String id) {
        Optional<Organization> organizationById = organizationRepository.findById(id);
        if (organizationById.isEmpty()){
            return organizationById;
        }
        organizationById.get().setNusach(Nusach.fromString(organizationById.get().getNusachStr()));
        return organizationById;
    }

    public List<Organization> getAll() {
        List<Organization> allOrgs = organizationRepository.findAll();
        setupOrgObjs(allOrgs);
        return allOrgs;
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

    private void setupOrgObjs(List<Organization> organizations) {
        for(Organization organization : organizations) {
            organization.setWebsiteURI(URI.create(organization.getWebsiteURIStr()));
            organization.setNusach(Nusach.fromString(organization.getNusachStr()));
        }
    }
}
