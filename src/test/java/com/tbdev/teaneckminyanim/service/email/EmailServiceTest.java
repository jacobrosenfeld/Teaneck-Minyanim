package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_FROM_ADDRESS;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_PROVIDER;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SES_ACCESS_KEY_ID;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SES_REGION;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SES_SECRET_ACCESS_KEY;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SMTP_HOST;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SMTP_PASSWORD;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SMTP_PORT;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SMTP_STARTTLS_ENABLED;
import static com.tbdev.teaneckminyanim.enums.SettingKey.EMAIL_SMTP_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendsViaSmtpWhenProviderIsSmtp() throws Exception {
        EmailSender smtpSender = sender(EmailProvider.SMTP);
        EmailSender sesSender = sender(EmailProvider.SES);
        EmailService service = new EmailService(settings(Map.of(
                EMAIL_PROVIDER.getKey(), "SMTP",
                EMAIL_SMTP_HOST.getKey(), "smtp.example.com",
                EMAIL_FROM_ADDRESS.getKey(), "no-reply@example.com"
        )), List.of(smtpSender, sesSender));

        EmailSendResult result = service.send(message());

        assertTrue(result.isSuccess());
        assertEquals(EmailProvider.SMTP, result.getProvider());
        verify(smtpSender).send(any(EmailMessage.class),
                argThat(emailSettings -> emailSettings.getProvider() == EmailProvider.SMTP));
        verify(sesSender, never()).send(any(), any());
    }

    @Test
    void sendsViaSesWhenProviderIsSes() throws Exception {
        EmailSender smtpSender = sender(EmailProvider.SMTP);
        EmailSender sesSender = sender(EmailProvider.SES);
        EmailService service = new EmailService(settings(Map.of(
                EMAIL_PROVIDER.getKey(), "SES",
                EMAIL_SES_REGION.getKey(), "us-east-1",
                EMAIL_SES_ACCESS_KEY_ID.getKey(), "AKIA_TEST",
                EMAIL_SES_SECRET_ACCESS_KEY.getKey(), "ses-secret",
                EMAIL_FROM_ADDRESS.getKey(), "no-reply@example.com"
        )), List.of(smtpSender, sesSender));

        EmailSendResult result = service.send(message());

        assertTrue(result.isSuccess());
        assertEquals(EmailProvider.SES, result.getProvider());
        verify(sesSender).send(any(EmailMessage.class),
                argThat(emailSettings -> emailSettings.getProvider() == EmailProvider.SES));
        verify(smtpSender, never()).send(any(), any());
    }

    @Test
    void invalidProviderFailsWithoutCallingSender() throws Exception {
        EmailSender smtpSender = sender(EmailProvider.SMTP);
        EmailService service = new EmailService(settings(Map.of(
                EMAIL_PROVIDER.getKey(), "sendgrid",
                EMAIL_SMTP_PASSWORD.getKey(), "smtp-secret",
                EMAIL_FROM_ADDRESS.getKey(), "no-reply@example.com"
        )), List.of(smtpSender));

        EmailSendResult result = service.send(message());

        assertFalse(result.isSuccess());
        assertNull(result.getProvider());
        assertFalse(result.getMessage().contains("smtp-secret"));
        verify(smtpSender, never()).send(any(), any());
    }

    @Test
    void senderFailureRedactsConfiguredSecrets() throws Exception {
        EmailSender smtpSender = sender(EmailProvider.SMTP);
        doThrow(new EmailSendingException("Bad auth for smtp-user with smtp-secret"))
                .when(smtpSender)
                .send(any(), any());
        EmailService service = new EmailService(settings(Map.of(
                EMAIL_PROVIDER.getKey(), "SMTP",
                EMAIL_SMTP_HOST.getKey(), "smtp.example.com",
                EMAIL_SMTP_USERNAME.getKey(), "smtp-user",
                EMAIL_SMTP_PASSWORD.getKey(), "smtp-secret",
                EMAIL_FROM_ADDRESS.getKey(), "no-reply@example.com"
        )), List.of(smtpSender));

        EmailSendResult result = service.send(message());

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains("smtp-user"));
        assertFalse(result.getMessage().contains("smtp-secret"));
        assertTrue(result.getMessage().contains("[secret]"));
    }

    private EmailSender sender(EmailProvider provider) {
        EmailSender sender = mock(EmailSender.class);
        when(sender.getProvider()).thenReturn(provider);
        return sender;
    }

    private ApplicationSettingsService settings(Map<String, String> values) {
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        when(settingsService.getEmailProvider()).thenReturn(values.getOrDefault(EMAIL_PROVIDER.getKey(), ""));
        when(settingsService.getEmailSmtpHost()).thenReturn(values.getOrDefault(EMAIL_SMTP_HOST.getKey(), ""));
        when(settingsService.getEmailSmtpPort())
                .thenReturn(Integer.parseInt(values.getOrDefault(EMAIL_SMTP_PORT.getKey(), "587")));
        when(settingsService.getEmailSmtpUsername()).thenReturn(values.getOrDefault(EMAIL_SMTP_USERNAME.getKey(), ""));
        when(settingsService.getEmailSmtpPassword()).thenReturn(values.getOrDefault(EMAIL_SMTP_PASSWORD.getKey(), ""));
        when(settingsService.isEmailSmtpStartTlsEnabled())
                .thenReturn(Boolean.parseBoolean(values.getOrDefault(EMAIL_SMTP_STARTTLS_ENABLED.getKey(), "true")));
        when(settingsService.getEmailFromAddress()).thenReturn(values.getOrDefault(EMAIL_FROM_ADDRESS.getKey(), ""));
        when(settingsService.getEmailFromName()).thenReturn("");
        when(settingsService.getEmailReplyTo()).thenReturn("");
        when(settingsService.getEmailSesRegion()).thenReturn(values.getOrDefault(EMAIL_SES_REGION.getKey(), ""));
        when(settingsService.getEmailSesAccessKeyId()).thenReturn(values.getOrDefault(EMAIL_SES_ACCESS_KEY_ID.getKey(), ""));
        when(settingsService.getEmailSesSecretAccessKey()).thenReturn(values.getOrDefault(EMAIL_SES_SECRET_ACCESS_KEY.getKey(), ""));
        when(settingsService.getEmailSesConfigurationSet()).thenReturn("");
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        return settingsService;
    }

    private EmailMessage message() {
        return EmailMessage.builder()
                .to("admin@example.com")
                .subject("Subject")
                .textBody("Body")
                .build();
    }
}
