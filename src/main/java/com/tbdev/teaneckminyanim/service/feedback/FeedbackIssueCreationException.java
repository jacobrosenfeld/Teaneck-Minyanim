package com.tbdev.teaneckminyanim.service.feedback;

public class FeedbackIssueCreationException extends Exception {
    public FeedbackIssueCreationException(String message) {
        super(message);
    }

    public FeedbackIssueCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
