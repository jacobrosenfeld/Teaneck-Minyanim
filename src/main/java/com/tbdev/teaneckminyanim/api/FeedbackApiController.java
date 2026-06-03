package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionRequest;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionResponse;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackConfigurationException;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackIssueCreationException;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackService;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackSubmissionResult;
import com.tbdev.teaneckminyanim.service.feedback.FeedbackValidationException;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
@Tag(name = "Feedback", description = "Public feedback submission endpoint")
public class FeedbackApiController {

    private final FeedbackService feedbackService;
    private final RecaptchaService recaptchaService;

    @PostMapping
    @Operation(
            summary = "Submit user feedback",
            description = "Creates a GitHub issue from categorized user feedback and optional automatically collected client metadata."
    )
    public ResponseEntity<ApiResponse<FeedbackSubmissionResponse>> submitFeedback(
            @RequestBody FeedbackSubmissionRequest request,
            HttpServletRequest httpRequest) {
        try {
            recaptchaService.verify(request == null ? null : request.recaptchaToken(), httpRequest);
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
}
