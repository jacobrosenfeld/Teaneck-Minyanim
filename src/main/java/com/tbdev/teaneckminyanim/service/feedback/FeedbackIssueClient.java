package com.tbdev.teaneckminyanim.service.feedback;

public interface FeedbackIssueClient {
    CreatedGitHubIssue createIssue(FeedbackIssueRequest request)
            throws FeedbackConfigurationException, FeedbackIssueCreationException;
}
