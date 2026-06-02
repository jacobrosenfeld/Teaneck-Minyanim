package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackSubmissionRequest(
        String message,
        String email,
        FeedbackMetadataDto metadata
) {
}
