package com.tbdev.teaneckminyanim.service.feedback;

import java.util.List;

public record FeedbackIssueRequest(String title, String body, List<String> labels) {
}
