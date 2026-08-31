package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionRequest;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionResponse;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackConfigurationException;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackIssueCreationException;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackService;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackSubmissionResult;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackValidationException;
import com.tbdev.teaneckminyanim.service.feedback.MobileFeedbackAuthService;
import com.tbdev.teaneckminyanim.service.feedback.MobileFeedbackAuthenticationException;
import com.tbdev.teaneckminyanim.service.feedback.ServerFeedbackContext;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaVerificationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
@Tag(name = "Feedback", description = "Public feedback submission endpoint")
public class FeedbackApiController {

    private final FeedbackService feedbackService;
    private final RecaptchaService recaptchaService;
    private final MobileFeedbackAuthService mobileFeedbackAuthService;

    @PostMapping
    @Operation(
            summary = "Submit user feedback",
            description = "Creates a GitHub issue from categorized user feedback and optional automatically collected client metadata. Web submissions use reCAPTCHA when configured. Native mobile submissions send X-Teaneck-Minyanim-App-Token and skip reCAPTCHA."
    )
    public ResponseEntity<ApiResponse<FeedbackSubmissionResponse>> submitFeedback(
            @RequestBody FeedbackSubmissionRequest request,
            HttpServletRequest httpRequest) {
        try {
            verifyClientSubmission(request, httpRequest);
            FeedbackSubmissionResult result = feedbackService.submit(
                    request,
                    ServerFeedbackContext.from(httpRequest));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(FeedbackSubmissionResponse.from(result)));
        } catch (FeedbackValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_FEEDBACK", e.getMessage()));
        } catch (RecaptchaVerificationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_RECAPTCHA", e.getMessage()));
        } catch (MobileFeedbackAuthenticationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("INVALID_MOBILE_APP_TOKEN",
                            "Mobile feedback authorization failed."));
        } catch (FeedbackConfigurationException e) {
            log.warn("Feedback submission rejected because infrastructure is not configured: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.err("FEEDBACK_NOT_CONFIGURED", e.getMessage()));
        } catch (FeedbackIssueCreationException e) {
            log.warn("Feedback GitHub issue creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.err("FEEDBACK_SUBMISSION_FAILED",
                            "Feedback could not be submitted right now. Please try again later."));
        }
    }

    private void verifyClientSubmission(FeedbackSubmissionRequest request, HttpServletRequest httpRequest)
            throws RecaptchaVerificationException, FeedbackConfigurationException, MobileFeedbackAuthenticationException {
        if (hasMobileAppToken(httpRequest) || isNativeMobileSubmission(request)) {
            mobileFeedbackAuthService.verify(httpRequest);
            return;
        }

        recaptchaService.verify(request == null ? null : request.recaptchaToken(), httpRequest);
    }

    private boolean hasMobileAppToken(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        String token = request.getHeader(MobileFeedbackAuthService.APP_TOKEN_HEADER);
        return token != null && !token.trim().isEmpty();
    }

    private boolean isNativeMobileSubmission(FeedbackSubmissionRequest request) {
        if (request == null || request.metadata() == null || request.metadata().platform() == null) {
            return false;
        }

        return switch (request.metadata().platform().trim().toLowerCase(Locale.ROOT)) {
            case "ios", "android", "mobile", "native" -> true;
            default -> false;
        };
    }
}
