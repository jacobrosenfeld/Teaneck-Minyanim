package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.OrganizationDto;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public REST API for organization data.
 *
 * GET /api/v1/organizations           → list all enabled organizations
 * GET /api/v1/organizations/{id}      → single organization by ID or slug
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
public class OrganizationApiController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> listOrganizations() {
        List<OrganizationDto> orgs = organizationService.getAll()
                .stream()
                .filter(Organization::isEnabled)
                .map(OrganizationDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(orgs, Map.of("count", orgs.size())));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(@PathVariable String idOrSlug) {
        // Try by ID first, then by slug
        Optional<Organization> orgOpt = organizationService.findById(idOrSlug);
        if (orgOpt.isEmpty()) {
            orgOpt = organizationService.findByUrlSlug(idOrSlug);
        }

        return orgOpt
                .filter(Organization::isEnabled)
                .map(org -> ResponseEntity.ok(ApiResponse.ok(OrganizationDto.from(org))))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.err("NOT_FOUND", "Organization not found: " + idOrSlug)));
    }
}
