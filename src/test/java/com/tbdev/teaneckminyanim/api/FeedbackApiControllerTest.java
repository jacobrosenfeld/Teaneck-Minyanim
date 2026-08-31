package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.FeedbackMetadataDto;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionRequest;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionResponse;
import com.tbdev.teaneckminyanim.service.feedback.CreatedGitHubIssue;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackService;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackSubmissionResult;
import com.tbdev.teaneckminyanim.service.feedback.MobileFeedbackAuthService;
import com.tbdev.teaneckminyanim.service.feedback.MobileFeedbackAuthenticationException;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedbackApiControllerTest {
    private FeedbackService feedbackService;
    private RecaptchaService recaptchaService;
    private MobileFeedbackAuthService mobileFeedbackAuthService;
    private FeedbackApiController controller;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        recaptchaService = mock(RecaptchaService.class);
        mobileFeedbackAuthService = mock(MobileFeedbackAuthService.class);
        controller = new FeedbackApiController(feedbackService, recaptchaService, mobileFeedbackAuthService);

        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRequestURL()).thenReturn(new StringBuffer("https://teaneckminyanim.com/api/v1/feedback"));
        when(httpRequest.getHeader("Origin")).thenReturn("https://teaneckminyanim.com");
        when(httpRequest.getHeader("Referer")).thenReturn("https://teaneckminyanim.com");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
    }

    @Test
    void webFeedbackVerifiesRecaptcha() throws Exception {
        FeedbackSubmissionRequest request = request("web", "recaptcha-token");
        when(feedbackService.submit(eq(request), any())).thenReturn(result());

        ResponseEntity<ApiResponse<FeedbackSubmissionResponse>> response =
                controller.submitFeedback(request, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(recaptchaService).verify("recaptcha-token", httpRequest);
        verifyNoInteractions(mobileFeedbackAuthService);
    }

    @Test
    void nativeMobileFeedbackUsesAppTokenAndSkipsRecaptcha() throws Exception {
        FeedbackSubmissionRequest request = request("ios", null);
        when(feedbackService.submit(eq(request), any())).thenReturn(result());

        ResponseEntity<ApiResponse<FeedbackSubmissionResponse>> response =
                controller.submitFeedback(request, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(mobileFeedbackAuthService).verify(httpRequest);
        verifyNoInteractions(recaptchaService);
    }

    @Test
    void nativeMobileFeedbackRejectsInvalidAppToken() throws Exception {
        FeedbackSubmissionRequest request = request("android", null);
        doThrow(new MobileFeedbackAuthenticationException("Bad token"))
                .when(mobileFeedbackAuthService).verify(httpRequest);

        ResponseEntity<ApiResponse<FeedbackSubmissionResponse>> response =
                controller.submitFeedback(request, httpRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().error());
        assertEquals("INVALID_MOBILE_APP_TOKEN", response.getBody().error().code());
        verifyNoInteractions(recaptchaService);
        verify(feedbackService, never()).submit(any(), any());
    }

    private FeedbackSubmissionRequest request(String platform, String recaptchaToken) {
        return new FeedbackSubmissionRequest(
                "Please check this.",
                "",
                "APP_FUNCTIONALITY",
                recaptchaToken,
                new FeedbackMetadataDto(
                        platform,
                        "home",
                        null,
                        "/",
                        "https://teaneckminyanim.com",
                        "1.1.6",
                        "36",
                        null,
                        null,
                        "iPhone",
                        "iOS",
                        "18.0",
                        "2026-08-30",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private FeedbackSubmissionResult result() {
        return new FeedbackSubmissionResult(
                "fb-test",
                new CreatedGitHubIssue(245, "https://github.com/example/repo/issues/245"),
                false,
                false,
                "Email provider is not configured.");
    }
}
