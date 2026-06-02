package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackMetadataDto(
        String platform,
        String screen,
        String page,
        String route,
        String url,
        String appVersion,
        String buildNumber,
        String browser,
        String userAgent,
        String deviceModel,
        String osName,
        String osVersion,
        String selectedDate,
        OrganizationContext organization,
        MinyanContext minyan,
        CalendarContext calendar,
        PostHogContext posthog,
        Map<String, String> filters,
        Map<String, String> routeParams,
        Map<String, String> extra
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrganizationContext(String id, String slug, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MinyanContext(
            String id,
            String type,
            String time,
            String date,
            String locationName
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalendarContext(
            String eventId,
            String entryId,
            String source,
            String sourceId
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostHogContext(
            String distinctId,
            String sessionId,
            String sessionReplayUrl
    ) {
    }
}
