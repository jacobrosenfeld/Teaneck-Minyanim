package com.tbdev.teaneckminyanim.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.NotificationDto;
import com.tbdev.teaneckminyanim.api.dto.admin.NotificationAdminDto;
import com.tbdev.teaneckminyanim.api.dto.admin.NotificationCreateRequest;
import com.tbdev.teaneckminyanim.api.dto.admin.NotificationDeleteResult;
import com.tbdev.teaneckminyanim.api.dto.admin.NotificationUpdateRequest;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.NotificationService;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationCommand;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationNotFoundException;
import com.tbdev.teaneckminyanim.service.auth.ApiAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Notifications API.
 *
 * <p>Public (no auth):
 * GET /api/v1/notifications           - all active notifications (any type)
 * GET /api/v1/notifications?type=BANNER  - banners only
 * GET /api/v1/notifications?type=POPUP   - popups only
 *
 * <p>Super admin (bearer token or API key with {@code notifications:write}): list every notification with
 * {@code includeInactive=true}, and create, update/toggle and delete them.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@Tag(name = "Notifications", description = "Active announcements and popup notifications")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final ApiAuthorizationService authorization;

    @GetMapping
    @Operation(
        summary = "List notifications",
        description = "Without `includeInactive`, this is public and returns all currently active notifications. " +
                      "Filter by type=BANNER or type=POPUP. " +
                      "The mobile app should use `maxDisplays` to stop showing a notification after N views, " +
                      "mirroring the website behavior.\n\n" +
                      "With `includeInactive=true` it requires a super admin credential holding " +
                      "`notifications:write` and returns every notification (enabled or not, expired or not) " +
                      "as admin objects with `enabled`, `expirationDate`, `createdAt` and `updatedAt`."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @SecurityRequirement(name = "")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notifications"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_TYPE", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "includeInactive without a valid token (UNAUTHENTICATED, INVALID_TOKEN)", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "includeInactive without super admin access or the notifications:write scope (FORBIDDEN, INSUFFICIENT_SCOPE)", content = @Content)
    })
    public ResponseEntity<ApiResponse<?>> getNotifications(
            @Parameter(description = "BANNER or POPUP")
            @RequestParam(required = false) String type,
            @Parameter(description = "Super admins only: include disabled and expired notifications, as admin objects.")
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {

        if (includeInactive) {
            authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE);
            String wanted = null;
            if (type != null) {
                wanted = type.toUpperCase();
                if (!wanted.equals("BANNER") && !wanted.equals("POPUP")) {
                    throw ApiException.validation("type must be BANNER or POPUP");
                }
            }
            final String filter = wanted;
            List<NotificationAdminDto> all = notificationService.getAll().stream()
                    .filter(n -> filter == null || filter.equalsIgnoreCase(n.getType()))
                    .sorted(java.util.Comparator.comparing(Notification::getCreatedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .map(NotificationAdminDto::from)
                    .toList();
            return ResponseEntity.ok(ApiResponse.ok(all, Map.of("count", all.size())));
        }

        List<NotificationDto> dtos;
        if (type != null) {
            String upperType = type.toUpperCase();
            if (!upperType.equals("BANNER") && !upperType.equals("POPUP")) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.err("INVALID_TYPE", "type must be BANNER or POPUP"));
            }
            dtos = notificationService.getActiveBanners().stream()
                    .filter(n -> n.getType().equalsIgnoreCase(upperType))
                    .map(NotificationDto::from)
                    .toList();
            // getActiveBanners only returns BANNER — need to handle POPUP separately
            if (upperType.equals("POPUP")) {
                dtos = notificationService.getActivePopups().stream()
                        .map(NotificationDto::from)
                        .toList();
            } else {
                dtos = notificationService.getActiveBanners().stream()
                        .map(NotificationDto::from)
                        .toList();
            }
        } else {
            // Both banners and popups
            List<NotificationDto> banners = notificationService.getActiveBanners().stream()
                    .map(NotificationDto::from).toList();
            List<NotificationDto> popups = notificationService.getActivePopups().stream()
                    .map(NotificationDto::from).toList();
            dtos = new java.util.ArrayList<>();
            dtos.addAll(banners);
            dtos.addAll(popups);
        }

        return ResponseEntity.ok(ApiResponse.ok(dtos, Map.of("count", dtos.size())));
    }

    @PostMapping
    @AdminAction("notification.create")
    @Operation(
        summary = "Create a notification",
        description = "Creates a site-wide banner or popup. Requires a super admin credential holding " +
                      "`notifications:write`; keys limited to specific shuls are refused. `enabled` defaults to true."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: missing title or message, type not BANNER or POPUP, maxDisplays below 1, unparseable expirationDate", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN or INSUFFICIENT_SCOPE", content = @Content)
    })
    public ResponseEntity<ApiResponse<NotificationAdminDto>> create(@RequestBody NotificationCreateRequest request) {
        authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE);
        if (request == null) {
            throw ApiException.validation("A request body is required.");
        }
        Notification saved = guarded(() -> notificationService.create(NotificationCommand.full(
                request.title(), request.message(), request.type(), request.enabled(),
                NotificationService.parseExpiration(request.expirationDate()), request.maxDisplays())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(NotificationAdminDto.from(saved)));
    }

    @PatchMapping("/{id}")
    @AdminAction({"notification.update", "notification.toggle"})
    @Operation(
        summary = "Update a notification",
        description = "Partial update: omitted fields are unchanged; `expirationDate` and `maxDisplays` can be set " +
                      "to null to clear them. Sending only `enabled` enables or disables it. Requires a super admin " +
                      "credential holding `notifications:write`."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = NotificationUpdateRequest.class)))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN or INSUFFICIENT_SCOPE", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND", content = @Content)
    })
    public ApiResponse<NotificationAdminDto> update(
            @Parameter(description = "Notification id") @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody JsonNode body) {
        authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("A JSON object body is required.");
        }
        LocalDateTime expiration = body.has("expirationDate")
                ? guarded(() -> NotificationService.parseExpiration(text(body, "expirationDate"))) : null;
        Integer maxDisplays = body.has("maxDisplays") ? integer(body, "maxDisplays") : null;
        NotificationCommand cmd = new NotificationCommand(
                body.has("title") ? requiredText(body, "title") : null,
                body.has("message") ? requiredText(body, "message") : null,
                body.has("type") ? requiredText(body, "type") : null,
                body.has("enabled") ? bool(body, "enabled") : null,
                expiration, body.has("expirationDate"),
                maxDisplays, body.has("maxDisplays"));
        Notification saved = guarded(() -> notificationService.update(id, cmd));
        return ApiResponse.ok(NotificationAdminDto.from(saved));
    }

    @DeleteMapping("/{id}")
    @AdminAction("notification.delete")
    @Operation(
        summary = "Delete a notification",
        description = "Permanently deletes a notification. Requires a super admin credential holding `notifications:write`."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN or INSUFFICIENT_SCOPE", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND", content = @Content)
    })
    public ApiResponse<NotificationDeleteResult> delete(
            @Parameter(description = "Notification id") @PathVariable String id) {
        authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE);
        guarded(() -> {
            notificationService.delete(id);
            return null;
        });
        return ApiResponse.ok(new NotificationDeleteResult(true, id));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Maps service exceptions to the API error codes. */
    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        } catch (NotificationNotFoundException e) {
            throw ApiException.notFound(e.getMessage());
        } catch (AccessDeniedException e) {
            throw ApiException.forbidden("Only super admins can manage notifications.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw ApiException.validation(field + " must be a string.");
        return node.asText();
    }

    private static String requiredText(JsonNode body, String field) {
        String value = text(body, field);
        if (value == null) throw ApiException.validation(field + " cannot be null.");
        return value;
    }

    private static Boolean bool(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isBoolean()) throw ApiException.validation(field + " must be true or false.");
        return node.asBoolean();
    }

    private static Integer integer(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isInt()) throw ApiException.validation(field + " must be a whole number or null.");
        return node.asInt();
    }
}
