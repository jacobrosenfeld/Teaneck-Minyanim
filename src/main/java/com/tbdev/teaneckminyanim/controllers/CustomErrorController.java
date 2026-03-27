package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.VersionService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    private final ApplicationSettingsService settingsService;
    private final VersionService versionService;

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response) {
        int statusCode = resolveStatusCode(request);
        response.setStatus(statusCode);

        String requestUri = resolveRequestUri(request);
        if (isApiRequest(requestUri)) {
            return buildApiErrorResponse(statusCode);
        }

        ErrorPageContent content = buildPageContent(statusCode, requestUri);
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("errorTitle", content.title());
        mv.addObject("errorMessage", content.message());
        mv.addObject("errorHint", content.hint());
        mv.addObject("errorCode", String.valueOf(statusCode));
        mv.addObject("primaryCtaLabel", content.primaryCtaLabel());
        mv.addObject("primaryCtaHref", content.primaryCtaHref());
        mv.addObject("secondaryCtaLabel", content.secondaryCtaLabel());
        mv.addObject("secondaryCtaHref", content.secondaryCtaHref());
        mv.addObject("errorIconPath", content.iconPath());
        mv.addObject("errorIconAlt", content.iconAlt());

        mv.addObject("siteName", settingsService.getSiteName());
        mv.addObject("supportEmail", settingsService.getSupportEmail());
        mv.addObject("appleSmartAppBannerContent", settingsService.getAppleSmartAppBannerContent());
        mv.addObject("appVersion", versionService.getVersion());

        return mv;
    }

    private int resolveStatusCode(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        try {
            return Integer.parseInt(status.toString());
        } catch (NumberFormatException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }

    private String resolveRequestUri(HttpServletRequest request) {
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestUri != null) {
            return requestUri.toString();
        }
        return request.getRequestURI();
    }

    private boolean isApiRequest(String requestUri) {
        return requestUri != null && requestUri.startsWith("/api/");
    }

    private ResponseEntity<ApiResponse<Void>> buildApiErrorResponse(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatusCode httpStatusCode = HttpStatusCode.valueOf(status.value());

        String code;
        String message;

        switch (status.value()) {
            case 400 -> {
                code = "BAD_REQUEST";
                message = "Invalid request";
            }
            case 403 -> {
                code = "FORBIDDEN";
                message = "You do not have permission to access this resource.";
            }
            case 404 -> {
                code = "NOT_FOUND";
                message = "Resource not found";
            }
            case 429 -> {
                code = "RATE_LIMITED";
                message = "Too many requests. Please try again later.";
            }
            case 503 -> {
                code = "SERVICE_UNAVAILABLE";
                message = "Service temporarily unavailable";
            }
            default -> {
                code = "INTERNAL_SERVER_ERROR";
                message = "An unexpected error occurred.";
            }
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(httpStatusCode);
        if (status.value() == 429) {
            builder.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return builder.body(ApiResponse.err(code, message));
    }

    private ErrorPageContent buildPageContent(int statusCode, String requestUri) {
        String retryPath = normalizeRetryPath(requestUri);
        return switch (statusCode) {
            case 400 -> new ErrorPageContent(
                    "That request was missing a few people for a minyan.",
                    "Some required information was invalid or missing.",
                    "Please review your input and try again.",
                    "Go Back",
                    retryPath,
                    "Back to Home",
                    "/",
                    "/assets/icons/errors/error-400-checklist.svg",
                    "Checklist with missing required fields"
            );
            case 403 -> new ErrorPageContent(
                    "Members only beyond this mechitza.",
                    "You are signed in without access to this section.",
                    "Use an account with the right permissions, or head back to a public page.",
                    "Back to Home",
                    "/",
                    "Admin Login",
                    "/admin/login",
                    "/assets/icons/errors/error-403-doorway.svg",
                    "Restricted doorway with a lock"
            );
            case 404 -> new ErrorPageContent(
                    "This page didn't make the minyan.",
                    "We could not find that route. It may have moved, or the URL is off by a letter.",
                    "Try one of the links below to get back on track.",
                    "Back to Home",
                    "/",
                    "Today's Minyanim",
                    "/zmanim",
                    "/assets/icons/errors/error-404-bulletin.svg",
                    "Bulletin board with a crossed-out room number"
            );
            case 429 -> new ErrorPageContent(
                    "Please don't enter the shul during the Rav's drasha.",
                    "Too many requests came in from your connection.",
                    "Please wait a minute and try again.",
                    "Retry in a Minute",
                    retryPath,
                    "Back to Home",
                    "/",
                    "/assets/icons/errors/error-429-drasha.svg",
                    "Please wait sign with a clock"
            );
            case 503 -> new ErrorPageContent(
                    "Between minyanim. We'll reopen shortly.",
                    "The site is temporarily unavailable for maintenance.",
                    "Please retry in a moment.",
                    "Retry",
                    retryPath,
                    "Back to Home",
                    "/",
                    "/assets/icons/errors/error-503-sign.svg",
                    "Closed sign on a doorway"
            );
            case 500 -> new ErrorPageContent(
                    "You missed borochu.",
                    "Something unexpected happened on our side.",
                    "Please try again. If this keeps happening, contact support.",
                    "Try Again",
                    retryPath,
                    "Back to Home",
                    "/",
                    "/assets/icons/errors/error-500-siddur.svg",
                    "Siddur and schedule pages dropped from a stand"
            );
            default -> new ErrorPageContent(
                    "Something Went Wrong",
                    "An unexpected error occurred.",
                    "Please try again, or return to the homepage.",
                    "Try Again",
                    retryPath,
                    "Back to Home",
                    "/",
                    "/assets/icons/errors/error-generic.svg",
                    "Generic warning icon"
            );
        };
    }

    private String normalizeRetryPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank() || "/error".equals(requestUri)) {
            return "/";
        }
        return requestUri;
    }

    private record ErrorPageContent(
            String title,
            String message,
            String hint,
            String primaryCtaLabel,
            String primaryCtaHref,
            String secondaryCtaLabel,
            String secondaryCtaHref,
            String iconPath,
            String iconAlt
    ) {
    }
}
