package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.auth.MeDto;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds the {@link MeDto} returned by {@code /api/v1/me} and by every sign-in and refresh. */
@Component
@RequiredArgsConstructor
public class AuthResponses {
    private final OrganizationService organizationService;

    /** For a just-issued app token: every scope, no shul restriction. */
    public MeDto forAppToken(TNMUser user) {
        return build(user, ApiAuthentication.Kind.APP, true, Set.of(), Set.of());
    }

    /** For the credential on the current request. */
    public MeDto forAuthentication(TNMUser user, ApiAuthentication auth) {
        return build(user, auth.getKind(), auth.isAppToken() || auth.isSameAsOwner(), auth.getScopes(),
                auth.getRestrictedOrganizationIds());
    }

    private MeDto build(TNMUser user, ApiAuthentication.Kind kind, boolean allScopes, Set<ApiScope> keyScopes,
                        Set<String> restrictedOrganizationIds) {
        List<String> scopes = (allScopes ? Arrays.stream(ApiScope.values()) : keyScopes.stream().sorted())
                .map(ApiScope::value)
                .toList();
        List<MeDto.OrganizationRef> organizations = organizations(user).stream()
                .filter(org -> restrictedOrganizationIds.isEmpty() || restrictedOrganizationIds.contains(org.getId()))
                .sorted(Comparator.comparing(org -> org.getName() == null ? "" : org.getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(org -> new MeDto.OrganizationRef(org.getId(), org.getName(), org.getUrlSlug()))
                .toList();
        return new MeDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.role() == null ? null : user.role().name(),
                user.isSuperAdmin(),
                organizations,
                kind.name(),
                scopes,
                allScopes,
                restrictedOrganizationIds.stream().sorted().toList());
    }

    /** Enabled shuls the account can manage: all of them for a super admin. */
    private List<Organization> organizations(TNMUser user) {
        if (user.isSuperAdmin()) {
            return organizationService.getAll().stream()
                    .filter(Objects::nonNull)
                    .filter(Organization::isEnabled)
                    .toList();
        }
        return user.getAccessibleOrganizationIds().stream()
                .map(id -> organizationService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(Organization::isEnabled)
                .toList();
    }
}
