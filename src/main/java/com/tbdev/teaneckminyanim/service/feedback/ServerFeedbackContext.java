package com.tbdev.teaneckminyanim.service.feedback;

import jakarta.servlet.http.HttpServletRequest;

public record ServerFeedbackContext(
        String requestUrl,
        String origin,
        String referer,
        String userAgent
) {
    public static ServerFeedbackContext from(HttpServletRequest request) {
        if (request == null) {
            return new ServerFeedbackContext(null, null, null, null);
        }

        String query = request.getQueryString();
        String url = request.getRequestURL().toString();
        if (query != null && !query.isBlank()) {
            url += "?" + query;
        }

        return new ServerFeedbackContext(
                url,
                request.getHeader("Origin"),
                request.getHeader("Referer"),
                request.getHeader("User-Agent"));
    }
}
