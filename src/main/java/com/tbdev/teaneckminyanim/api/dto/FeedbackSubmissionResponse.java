package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackSubmissionResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackSubmissionResponse(
        String feedbackId,
        Integer githubIssueNumber,
        String githubIssueUrl,
        boolean userEmailProvided,
        boolean notificationEmailSent,
        String notificationEmailMessage
) {
    public static FeedbackSubmissionResponse from(FeedbackSubmissionResult result) {
        return new FeedbackSubmissionResponse(
                result.feedbackId(),
                result.githubIssue().number(),
                result.githubIssue().url(),
                result.userEmailProvided(),
                result.notificationEmailSent(),
                result.notificationEmailMessage());
    }
}
