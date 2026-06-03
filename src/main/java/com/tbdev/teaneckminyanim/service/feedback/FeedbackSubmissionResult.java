package com.tbdev.teaneckminyanim.service.feedback;

public record FeedbackSubmissionResult(
        String feedbackId,
        CreatedGitHubIssue githubIssue,
        boolean userEmailProvided,
        boolean notificationEmailSent,
        String notificationEmailMessage
) {
}
