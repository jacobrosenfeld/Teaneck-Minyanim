package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.OrganizationDto;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Organizations", description = "Synagogues and minyan groups")
public class OrganizationApiController {

    private final OrganizationService organizationService;

    @GetMapping
    @Operation(summary = "List all enabled organizations",
               description = "Returns id, name, slug, color, nusach, address, website, and whatsapp for every enabled org.")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> listOrganizations() {
        List<OrganizationDto> orgs = organizationService.getAll()
                .stream()
                .filter(Organization::isEnabled)
                .map(OrganizationDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(orgs, Map.of("count", orgs.size())));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Get a single organization",
               description = "Accepts either the internal organization ID or the URL slug.")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(
            @Parameter(description = "Organization ID or slug", example = "bmob")
            @PathVariable String idOrSlug) {
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
