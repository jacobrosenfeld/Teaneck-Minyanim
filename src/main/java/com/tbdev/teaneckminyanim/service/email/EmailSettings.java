package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.Getter;

import java.util.List;

@Getter
public class EmailSettings {
    private final EmailProvider provider;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean smtpStartTlsEnabled;
    private final String fromAddress;
    private final String fromName;
    private final String replyTo;
    private final String sesRegion;
    private final String sesAccessKeyId;
    private final String sesSecretAccessKey;
    private final String sesConfigurationSet;

    private EmailSettings(
            EmailProvider provider,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            boolean smtpStartTlsEnabled,
            String fromAddress,
            String fromName,
            String replyTo,
            String sesRegion,
            String sesAccessKeyId,
            String sesSecretAccessKey,
            String sesConfigurationSet) {
        this.provider = provider;
        this.smtpHost = normalize(smtpHost);
        this.smtpPort = smtpPort;
        this.smtpUsername = normalize(smtpUsername);
        this.smtpPassword = normalize(smtpPassword);
        this.smtpStartTlsEnabled = smtpStartTlsEnabled;
        this.fromAddress = normalize(fromAddress);
        this.fromName = normalize(fromName);
        this.replyTo = normalize(replyTo);
        this.sesRegion = normalize(sesRegion);
        this.sesAccessKeyId = normalize(sesAccessKeyId);
        this.sesSecretAccessKey = normalize(sesSecretAccessKey);
        this.sesConfigurationSet = normalize(sesConfigurationSet);
    }

    public static EmailSettings from(ApplicationSettingsService settingsService, EmailProvider provider) {
        Integer smtpPort = settingsService.getEmailSmtpPort();

        return new EmailSettings(
                provider,
                settingsService.getEmailSmtpHost(),
                smtpPort == null ? 587 : smtpPort,
                settingsService.getEmailSmtpUsername(),
                settingsService.getEmailSmtpPassword(),
                Boolean.TRUE.equals(settingsService.isEmailSmtpStartTlsEnabled()),
                settingsService.getEmailFromAddress(),
                settingsService.getEmailFromName(),
                settingsService.getEmailReplyTo(),
                settingsService.getEmailSesRegion(),
                settingsService.getEmailSesAccessKeyId(),
                settingsService.getEmailSesSecretAccessKey(),
                settingsService.getEmailSesConfigurationSet());
    }

    public void validateForProvider() throws EmailConfigurationException {
        if (provider == null) {
            throw new EmailConfigurationException("Email provider is not configured.");
        }

        requireEmail(fromAddress, "Email from address");
        if (EmailMessage.hasText(replyTo)) {
            requireEmail(replyTo, "Email reply-to address");
        }

        switch (provider) {
            case SMTP -> validateSmtp();
            case SES -> validateSes();
        }
    }

    public String redactSecrets(String message) {
        if (message == null) {
            return "Email send failed.";
        }

        String sanitized = message;
        for (String secret : List.of(smtpUsername, smtpPassword, sesAccessKeyId, sesSecretAccessKey)) {
            if (EmailMessage.hasText(secret)) {
                sanitized = sanitized.replace(secret, "[secret]");
            }
        }
        return sanitized;
    }

    private void validateSmtp() throws EmailConfigurationException {
        require(smtpHost, "SMTP host");
        if (smtpPort < 1 || smtpPort > 65535) {
            throw new EmailConfigurationException("SMTP port must be between 1 and 65535.");
        }
    }

    private void validateSes() throws EmailConfigurationException {
        require(sesRegion, "AWS SES region");
        require(sesAccessKeyId, "AWS SES access key ID");
        require(sesSecretAccessKey, "AWS SES secret access key");
    }

    private void require(String value, String label) throws EmailConfigurationException {
        if (!EmailMessage.hasText(value)) {
            throw new EmailConfigurationException(label + " is required for " + provider + " email delivery.");
        }
    }

    private void requireEmail(String value, String label) throws EmailConfigurationException {
        require(value, label);
        try {
            InternetAddress address = new InternetAddress(value);
            address.validate();
        } catch (AddressException e) {
            throw new EmailConfigurationException(label + " must be a valid email address.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
