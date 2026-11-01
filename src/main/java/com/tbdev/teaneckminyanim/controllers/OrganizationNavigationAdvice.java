package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Comparator;
import java.util.List;

@ControllerAdvice(basePackages = "com.tbdev.teaneckminyanim.controllers")
@RequiredArgsConstructor
public class OrganizationNavigationAdvice {
    private final TNMUserService users;
    private final OrganizationService organizations;

    @ModelAttribute("accessibleOrganizations")
    public List<Organization> accessibleOrganizations() {
        TNMUser user = users.getCurrentUser();
        if (user == null || user.isDisabled()) return List.of();
        return organizations.getAll().stream().filter(org -> user.canAccessOrganization(org.getId()))
                .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
