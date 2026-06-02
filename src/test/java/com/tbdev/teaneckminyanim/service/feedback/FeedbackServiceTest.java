package com.tbdev.teaneckminyanim.service.feedback;

import com.tbdev.teaneckminyanim.api.dto.FeedbackMetadataDto;
import com.tbdev.teaneckminyanim.api.dto.FeedbackSubmissionRequest;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailProvider;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

    @Test
    void submitCreatesGithubIssueAndSendsPrivateEmail() throws Exception {
        FeedbackIssueClient issueClient = mock(FeedbackIssueClient.class);
        EmailService emailService = mock(EmailService.class);
        ApplicationSettingsService settingsService = settings();
        FeedbackService service = new FeedbackService(issueClient, emailService, settingsService);

        CreatedGitHubIssue issue = new CreatedGitHubIssue(
                245,
                "https://github.com/jacobrosenfeld/Teaneck-Minyanim/issues/245");
        when(issueClient.createIssue(any())).thenReturn(issue);
        when(emailService.send(any())).thenReturn(
                EmailSendResult.success(EmailProvider.SMTP, "Email sent successfully."));

        FeedbackSubmissionResult result = service.submit(
                new FeedbackSubmissionRequest(
                        "The Shacharis time looks wrong for this shul.",
                        "user@example.com",
                        metadata()),
                new ServerFeedbackContext(
                        "https://www.teaneckminyanim.com/api/v1/feedback",
                        "https://www.teaneckminyanim.com",
                        "https://www.teaneckminyanim.com/bnai-yeshurun",
                        "Mozilla/5.0"));

        assertEquals(245, result.githubIssue().number());
        assertTrue(result.userEmailProvided());
        assertTrue(result.notificationEmailSent());

        ArgumentCaptor<FeedbackIssueRequest> issueCaptor = ArgumentCaptor.forClass(FeedbackIssueRequest.class);
        verify(issueClient).createIssue(issueCaptor.capture());
        FeedbackIssueRequest issueRequest = issueCaptor.getValue();
        assertTrue(issueRequest.title().startsWith("[Data]"));
        assertTrue(issueRequest.body().contains("The Shacharis time looks wrong"));
        assertTrue(issueRequest.body().contains("posthog-session-123"));
        assertTrue(issueRequest.body().contains("Bnai Yeshurun"));
        assertFalse(issueRequest.body().contains("user@example.com"));
        assertTrue(issueRequest.labels().contains("user feedback"));

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(emailCaptor.capture());
        EmailMessage email = emailCaptor.getValue();
        assertEquals("user@example.com", email.getTo().getFirst());
        assertEquals("info@teaneckminyanim.com", email.getCc().getFirst());
        assertTrue(email.getTextBody().contains(issue.url()));
        assertTrue(email.getTextBody().contains("The Shacharis time looks wrong"));
    }

    @Test
    void submitWithoutUserEmailSendsAdminNotification() throws Exception {
        FeedbackIssueClient issueClient = mock(FeedbackIssueClient.class);
        EmailService emailService = mock(EmailService.class);
        FeedbackService service = new FeedbackService(issueClient, emailService, settings());

        when(issueClient.createIssue(any())).thenReturn(
                new CreatedGitHubIssue(246, "https://github.com/example/repo/issues/246"));
        when(emailService.send(any())).thenReturn(
                EmailSendResult.success(EmailProvider.SES, "Email sent successfully."));

        FeedbackSubmissionResult result = service.submit(
                new FeedbackSubmissionRequest("The app crashed on the map screen.", "", null),
                null);

        assertFalse(result.userEmailProvided());
        assertTrue(result.notificationEmailSent());

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(emailCaptor.capture());
        EmailMessage email = emailCaptor.getValue();
        assertEquals("info@teaneckminyanim.com", email.getTo().getFirst());
        assertTrue(email.getCc().isEmpty());
        assertTrue(email.getTextBody().contains("No user email address was provided."));
    }

    @Test
    void emailFailureDoesNotFailSubmission() throws Exception {
        FeedbackIssueClient issueClient = mock(FeedbackIssueClient.class);
        EmailService emailService = mock(EmailService.class);
        FeedbackService service = new FeedbackService(issueClient, emailService, settings());

        when(issueClient.createIssue(any())).thenReturn(
                new CreatedGitHubIssue(247, "https://github.com/example/repo/issues/247"));
        when(emailService.send(any())).thenReturn(
                EmailSendResult.failure(null, "Email provider is not configured."));

        FeedbackSubmissionResult result = service.submit(
                new FeedbackSubmissionRequest("Please check this page.", "user@example.com", null),
                null);

        assertEquals(247, result.githubIssue().number());
        assertFalse(result.notificationEmailSent());
        assertEquals("Email provider is not configured.", result.notificationEmailMessage());
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        FeedbackIssueClient issueClient = mock(FeedbackIssueClient.class);
        EmailService emailService = mock(EmailService.class);
        FeedbackService service = new FeedbackService(issueClient, emailService, settings());

        assertThrows(FeedbackValidationException.class,
                () -> service.submit(new FeedbackSubmissionRequest(" ", "user@example.com", null), null));
        verify(issueClient, never()).createIssue(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        FeedbackIssueClient issueClient = mock(FeedbackIssueClient.class);
        EmailService emailService = mock(EmailService.class);
        FeedbackService service = new FeedbackService(issueClient, emailService, settings());

        assertThrows(FeedbackValidationException.class,
                () -> service.submit(new FeedbackSubmissionRequest("A real message", "not-email", null), null));
        verify(issueClient, never()).createIssue(any());
        verify(emailService, never()).send(any());
    }

    private FeedbackMetadataDto metadata() {
        return new FeedbackMetadataDto(
                "web",
                "organization-detail",
                "org",
                "/bnai-yeshurun",
                "https://www.teaneckminyanim.com/bnai-yeshurun",
                null,
                null,
                "Safari",
                "Mozilla/5.0",
                null,
                "macOS",
                "15.5",
                "2026-06-02",
                new FeedbackMetadataDto.OrganizationContext("O123", "bnai-yeshurun", "Bnai Yeshurun"),
                new FeedbackMetadataDto.MinyanContext("cal-123", "SHACHARIS", "07:00", "2026-06-02", "Main Shul"),
                null,
                new FeedbackMetadataDto.PostHogContext(
                        "posthog-user-123",
                        "posthog-session-123",
                        "https://us.posthog.com/replay/123"),
                Map.of("type", "Shacharis"),
                Map.of("slug", "bnai-yeshurun"),
                Map.of("component", "MinyanCard"));
    }

    private ApplicationSettingsService settings() {
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        when(settingsService.getSupportEmail()).thenReturn("info@teaneckminyanim.com");
        return settingsService;
    }
}
