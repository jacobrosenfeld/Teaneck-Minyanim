package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.model.Account;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.tools.SlugGenerator;
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
//        organizationByName.get().setNusach(Nusach.fromString(organizationByName.get().getNusachStr()));
        return organizationByName.get();
    }

    public Optional<Organization> findById(String id) {
        Optional<Organization> organizationById = organizationRepository.findById(id);
        if (organizationById.isEmpty()){
            return organizationById;
        }
//        organizationById.get().setNusach(Nusach.fromString(organizationById.get().getNusachStr()));
        return organizationById;
    }

    public Optional<Organization> findByUrlSlug(String urlSlug) {
        return organizationRepository.findByUrlSlug(urlSlug);
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
                   .email(account.getEmail())
                   .username(account.getUsername())
                   .encryptedPassword(account.getEncryptedPassword())
                   .organizationId(account.getOrganizationId())
                   .roleId(Integer.parseInt(account.getRoleId()))
                   .build());

        }
        return users;
    }

    public void setupOrg(Organization organization) {
        if (organization != null) {
            String websiteURIStr = organization.getWebsiteURIStr();
            if (websiteURIStr != null && !websiteURIStr.isEmpty()) {
                organization.setWebsiteURI(URI.create(websiteURIStr));
            } else {
                organization.setWebsiteURI(null);  // Or handle as needed if URI is missing
            }
        }
    }

    private void setupOrgObjs(List<Organization> organizations) {
        for (Organization organization : organizations) {
            setupOrg(organization);
        }
    }

    /**
     * Generates a unique URL slug for an organization.
     * If slug already exists, appends a number to make it unique.
     * 
     * @param baseSlug The base slug to start with
     * @return A unique slug that doesn't exist in the database
     */
    public String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 0;
        
        while (organizationRepository.findByUrlSlug(slug).isPresent()) {
            counter++;
            slug = SlugGenerator.generateUniqueSlug(baseSlug, counter);
        }
        
        return slug;
    }

    /**
     * Ensures an organization has a URL slug.
     * If not present, generates one from the organization name.
     * 
     * @param organization The organization to ensure has a slug
     */
    public void ensureSlug(Organization organization) {
        if (organization.getUrlSlug() == null || organization.getUrlSlug().trim().isEmpty()) {
            String baseSlug = SlugGenerator.generateSlug(organization.getName());
            String uniqueSlug = generateUniqueSlug(baseSlug);
            organization.setUrlSlug(uniqueSlug);
        }
    }
}
