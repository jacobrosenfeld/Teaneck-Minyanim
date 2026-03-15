package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.NotificationDto;
import com.tbdev.teaneckminyanim.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public REST API for active announcements / notifications.
 *
 * GET /api/v1/notifications           → all active notifications (any type)
 * GET /api/v1/notifications?type=BANNER  → banners only
 * GET /api/v1/notifications?type=POPUP   → popups only
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@Tag(name = "Notifications", description = "Active announcements and popup notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
        summary = "List active notifications",
        description = "Returns all currently active notifications. Filter by type=BANNER or type=POPUP. " +
                      "The mobile app should use `maxDisplays` to stop showing a notification after N views, " +
                      "mirroring the website behavior."
    )
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications(
            @RequestParam(required = false) String type) {

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
}
