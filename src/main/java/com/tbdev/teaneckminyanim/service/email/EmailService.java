package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {
    private final ApplicationSettingsService settingsService;
    private final Map<EmailProvider, EmailSender> sendersByProvider;

    public EmailService(ApplicationSettingsService settingsService, List<EmailSender> senders) {
        this.settingsService = settingsService;
        this.sendersByProvider = new EnumMap<>(EmailProvider.class);
        for (EmailSender sender : senders) {
            this.sendersByProvider.put(sender.getProvider(), sender);
        }
    }

    public EmailSendResult send(EmailMessage message) {
        EmailProvider provider = EmailProvider.fromSetting(settingsService.getEmailProvider()).orElse(null);
        EmailSettings settings = EmailSettings.from(settingsService, provider);

        try {
            message.validate();
            settings.validateForProvider();

            EmailSender sender = sendersByProvider.get(provider);
            if (sender == null) {
                return failure(provider, "Email provider is not available.");
            }

            sender.send(message, settings);
            log.info("Email send succeeded via {} to {} recipient(s)", provider, message.recipientCount());
            return EmailSendResult.success(provider, "Email sent successfully.");
        } catch (EmailConfigurationException e) {
            return failure(provider, e.getMessage());
        } catch (EmailSendingException e) {
            return failure(provider, settings.redactSecrets(e.getMessage()));
        } catch (RuntimeException e) {
            String sanitizedMessage = settings.redactSecrets(e.getMessage());
            log.error("Unexpected email send failure via {}: {} ({})",
                    provider,
                    sanitizedMessage,
                    e.getClass().getName());
            return EmailSendResult.failure(provider, "Email send failed. Check application logs for details.");
        }
    }

    public EmailSendResult sendTestEmail(String recipient) {
        EmailMessage message = EmailMessage.builder()
                .to(recipient)
                .subject("Test email from " + settingsService.getSiteName())
                .textBody("This is a test email from " + settingsService.getSiteName()
                        + ". If you received it, email delivery is configured correctly.")
                .htmlBody("<p>This is a test email from <strong>"
                        + HtmlUtils.htmlEscape(settingsService.getSiteName())
                        + "</strong>.</p><p>If you received it, email delivery is configured correctly.</p>")
                .metadata("source", "admin-test-email")
                .build();

        return send(message);
    }

    private EmailSendResult failure(EmailProvider provider, String message) {
        log.warn("Email send skipped/failed via {}: {}", provider, message);
        return EmailSendResult.failure(provider, message);
    }
}
