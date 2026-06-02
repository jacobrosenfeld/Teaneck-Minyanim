package com.tbdev.teaneckminyanim.service.feedback;

import com.tbdev.teaneckminyanim.api.dto.FeedbackMetadataDto;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionRequest;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {
    private static final int MAX_MESSAGE_LENGTH = 5_000;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final String FALLBACK_SUPPORT_EMAIL = "info@teaneckminyanim.com";

    private final FeedbackIssueClient issueClient;
    private final EmailService emailService;
    private final ApplicationSettingsService settingsService;

    public FeedbackSubmissionResult submit(
            FeedbackSubmissionRequest request,
            ServerFeedbackContext serverContext)
            throws FeedbackValidationException, FeedbackConfigurationException, FeedbackIssueCreationException {
        NormalizedFeedback feedback = normalizeAndValidate(request);
        String feedbackId = "fb-" + UUID.randomUUID();
        Instant submittedAt = Instant.now();

        FeedbackIssueRequest issueRequest = new FeedbackIssueRequest(
                buildIssueTitle(feedback.message(), feedback.metadata()),
                buildIssueBody(feedbackId, feedback, serverContext, submittedAt));
        CreatedGitHubIssue issue = issueClient.createIssue(issueRequest);

        EmailSendResult emailResult = sendNotificationEmail(feedbackId, feedback, issue, submittedAt);
        boolean emailSent = emailResult != null && emailResult.isSuccess();
        String emailMessage = emailResult == null ? "Email was not attempted." : emailResult.getMessage();

        if (!emailSent) {
            log.warn("Feedback {} created GitHub issue #{} but notification email was not sent: {}",
                    feedbackId,
                    issue.number(),
                    emailMessage);
        }

        return new FeedbackSubmissionResult(
                feedbackId,
                issue,
                hasText(feedback.email()),
                emailSent,
                emailMessage);
    }

    private NormalizedFeedback normalizeAndValidate(FeedbackSubmissionRequest request)
            throws FeedbackValidationException {
        if (request == null) {
            throw new FeedbackValidationException("Feedback request cannot be empty.");
        }

        String message = normalize(request.message());
        if (message.isBlank()) {
            throw new FeedbackValidationException("Feedback message cannot be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new FeedbackValidationException("Feedback message must be " + MAX_MESSAGE_LENGTH + " characters or fewer.");
        }

        String email = normalize(request.email());
        if (!email.isBlank()) {
            validateEmail(email);
        }

        return new NormalizedFeedback(message, email, request.metadata());
    }

    private void validateEmail(String email) throws FeedbackValidationException {
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (AddressException e) {
            throw new FeedbackValidationException("Email must be a valid email address.");
        }
    }

    private String buildIssueTitle(String message, FeedbackMetadataDto metadata) {
        String prefix = issuePrefix(metadata);
        String summary = message
                .replaceAll("\\s+", " ")
                .replaceAll("[\\p{Cntrl}&&[^\t]]", "")
                .trim();
        if (summary.isBlank()) {
            summary = "User feedback";
        }

        String title = prefix + " " + summary;
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH - 1).trim() + "...";
    }

    private String issuePrefix(FeedbackMetadataDto metadata) {
        if (metadata == null) {
            return "[Feedback]";
        }
        if (metadata.minyan() != null || metadata.calendar() != null) {
            return "[Data]";
        }

        String platform = normalize(metadata.platform()).toLowerCase();
        if (platform.equals("ios") || platform.equals("android")) {
            return "[App]";
        }
        if (platform.equals("web") || hasText(metadata.url())) {
            return "[Web]";
        }
        return "[Feedback]";
    }

    private String buildIssueBody(
            String feedbackId,
            NormalizedFeedback feedback,
            ServerFeedbackContext serverContext,
            Instant submittedAt) {
        StringBuilder body = new StringBuilder();
        appendSection(body, "User message");
        body.append(codeBlock(feedback.message())).append("\n\n");

        appendSection(body, "Submission");
        appendLine(body, "Feedback ID", feedbackId);
        appendLine(body, "Submitted at", submittedAt.toString());
        appendLine(body, "Inferred source", issuePrefix(feedback.metadata()));
        body.append("\n");

        appendClientContext(body, feedback.metadata());
        appendServerContext(body, serverContext);
        return body.toString().trim();
    }

    private void appendClientContext(StringBuilder body, FeedbackMetadataDto metadata) {
        appendSection(body, "Client context");
        if (metadata == null) {
            body.append("- No client metadata provided.\n\n");
            return;
        }

        appendLine(body, "Platform", metadata.platform());
        appendLine(body, "Screen", metadata.screen());
        appendLine(body, "Page", metadata.page());
        appendLine(body, "Route", metadata.route());
        appendLine(body, "URL", metadata.url());
        appendLine(body, "Selected date", metadata.selectedDate());
        appendLine(body, "App version", metadata.appVersion());
        appendLine(body, "Build number", metadata.buildNumber());
        appendLine(body, "Browser", metadata.browser());
        appendLine(body, "User agent", metadata.userAgent());
        appendLine(body, "Device model", metadata.deviceModel());
        appendLine(body, "OS", join(metadata.osName(), metadata.osVersion()));
        appendOrganizationContext(body, metadata.organization());
        appendMinyanContext(body, metadata.minyan());
        appendCalendarContext(body, metadata.calendar());
        appendPostHogContext(body, metadata.posthog());
        appendMap(body, "Filters", metadata.filters());
        appendMap(body, "Route params", metadata.routeParams());
        appendMap(body, "Extra", metadata.extra());
        body.append("\n");
    }

    private void appendOrganizationContext(
            StringBuilder body,
            FeedbackMetadataDto.OrganizationContext organization) {
        if (organization == null) {
            return;
        }
        appendLine(body, "Organization", join(organization.name(), organization.slug(), organization.id()));
    }

    private void appendMinyanContext(StringBuilder body, FeedbackMetadataDto.MinyanContext minyan) {
        if (minyan == null) {
            return;
        }
        appendLine(body, "Minyan", join(minyan.type(), minyan.time(), minyan.date(), minyan.locationName(), minyan.id()));
    }

    private void appendCalendarContext(StringBuilder body, FeedbackMetadataDto.CalendarContext calendar) {
        if (calendar == null) {
            return;
        }
        appendLine(body, "Calendar event", join(calendar.eventId(), calendar.entryId(), calendar.source(), calendar.sourceId()));
    }

    private void appendPostHogContext(StringBuilder body, FeedbackMetadataDto.PostHogContext posthog) {
        if (posthog == null) {
            return;
        }
        appendLine(body, "PostHog distinct ID", posthog.distinctId());
        appendLine(body, "PostHog session ID", posthog.sessionId());
        appendLine(body, "PostHog session replay", posthog.sessionReplayUrl());
    }

    private void appendServerContext(StringBuilder body, ServerFeedbackContext context) {
        appendSection(body, "Request context");
        if (context == null) {
            body.append("- No server request metadata provided.\n");
            return;
        }
        appendLine(body, "Origin", context.origin());
        appendLine(body, "Referer", context.referer());
        appendLine(body, "Server user agent", context.userAgent());
    }

    private EmailSendResult sendNotificationEmail(
            String feedbackId,
            NormalizedFeedback feedback,
            CreatedGitHubIssue issue,
            Instant submittedAt) {
        String supportEmail = configuredSupportEmail();
        var message = EmailMessage.builder()
                .subject("Feedback received: GitHub issue #" + issue.number())
                .textBody(emailTextBody(feedbackId, feedback, issue, submittedAt, supportEmail))
                .htmlBody(emailHtmlBody(feedbackId, feedback, issue, submittedAt, supportEmail))
                .metadata("source", "feedback")
                .metadata("feedback_id", feedbackId)
                .metadata("github_issue", String.valueOf(issue.number()));

        if (hasText(feedback.email())) {
            message.to(feedback.email());
            if (hasText(supportEmail)) {
                message.cc(supportEmail);
            }
        } else {
            message.to(supportEmail);
        }

        return emailService.send(message.build());
    }

    private String emailTextBody(
            String feedbackId,
            NormalizedFeedback feedback,
            CreatedGitHubIssue issue,
            Instant submittedAt,
            String supportEmail) {
        StringBuilder body = new StringBuilder();
        if (hasText(feedback.email())) {
            body.append("Thanks for sending feedback to Teaneck Minyanim.\n\n");
        } else {
            body.append("A new Teaneck Minyanim feedback item was submitted.\n\n");
        }
        body.append("GitHub issue: ").append(issue.url()).append("\n");
        body.append("Feedback ID: ").append(feedbackId).append("\n");
        body.append("Submitted at: ").append(submittedAt).append("\n\n");
        body.append("Message:\n").append(feedback.message()).append("\n\n");
        if (hasText(feedback.email())) {
            body.append("You can reply to this email or follow the GitHub issue link above. ");
            body.append(supportEmail).append(" has been copied for follow-up.\n");
        } else {
            body.append("No user email address was provided.\n");
        }
        return body.toString();
    }

    private String emailHtmlBody(
            String feedbackId,
            NormalizedFeedback feedback,
            CreatedGitHubIssue issue,
            Instant submittedAt,
            String supportEmail) {
        StringBuilder body = new StringBuilder();
        body.append("<p>");
        if (hasText(feedback.email())) {
            body.append("Thanks for sending feedback to Teaneck Minyanim.");
        } else {
            body.append("A new Teaneck Minyanim feedback item was submitted.");
        }
        body.append("</p>");
        body.append("<p><strong>GitHub issue:</strong> <a href=\"")
                .append(HtmlUtils.htmlEscape(issue.url()))
                .append("\">")
                .append(HtmlUtils.htmlEscape(issue.url()))
                .append("</a></p>");
        body.append("<p><strong>Feedback ID:</strong> ")
                .append(HtmlUtils.htmlEscape(feedbackId))
                .append("<br><strong>Submitted at:</strong> ")
                .append(HtmlUtils.htmlEscape(submittedAt.toString()))
                .append("</p>");
        body.append("<p><strong>Message:</strong></p><pre style=\"white-space:pre-wrap\">")
                .append(HtmlUtils.htmlEscape(feedback.message()))
                .append("</pre>");
        if (hasText(feedback.email())) {
            body.append("<p>You can reply to this email or follow the GitHub issue link above. ")
                    .append(HtmlUtils.htmlEscape(supportEmail))
                    .append(" has been copied for follow-up.</p>");
        } else {
            body.append("<p>No user email address was provided.</p>");
        }
        return body.toString();
    }

    private String configuredSupportEmail() {
        String supportEmail = normalize(settingsService.getSupportEmail());
        return supportEmail.isBlank() ? FALLBACK_SUPPORT_EMAIL : supportEmail;
    }

    private void appendSection(StringBuilder body, String title) {
        body.append("## ").append(title).append("\n");
    }

    private void appendLine(StringBuilder body, String label, String value) {
        if (hasText(value)) {
            body.append("- **").append(label).append(":** ").append(value.trim()).append("\n");
        }
    }

    private void appendMap(StringBuilder body, String label, Map<String, String> values) {
        Map<String, String> normalized = normalizeMap(values);
        if (normalized.isEmpty()) {
            return;
        }

        appendLine(body, label, normalized.toString());
    }

    private Map<String, String> normalizeMap(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (values == null) {
            return normalized;
        }

        values.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            String normalizedValue = normalize(value);
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return normalized;
    }

    private String codeBlock(String value) {
        return "```text\n" + value.replace("```", "` ` `") + "\n```";
    }

    private String join(String... values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isBlank()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(" / ");
            }
            joined.append(normalized);
        }
        return joined.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record NormalizedFeedback(
            String message,
            String email,
            FeedbackMetadataDto metadata
    ) {
    }
}
