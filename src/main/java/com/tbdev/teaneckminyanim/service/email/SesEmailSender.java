package com.tbdev.teaneckminyanim.service.email;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

@Component
public class SesEmailSender implements EmailSender {
    private static final int SES_TAG_VALUE_MAX_LENGTH = 256;

    @Override
    public EmailProvider getProvider() {
        return EmailProvider.SES;
    }

    @Override
    public void send(EmailMessage message, EmailSettings settings)
            throws EmailConfigurationException, EmailSendingException {
        SendEmailRequest request = buildRequest(message, settings);

        try (SesClient client = SesClient.builder()
                .region(Region.of(settings.getSesRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(settings.getSesAccessKeyId(), settings.getSesSecretAccessKey())))
                .build()) {
            client.sendEmail(request);
        } catch (SdkException e) {
            throw new EmailSendingException("SES email send failed: " + e.getMessage(), e);
        }
    }

    private SendEmailRequest buildRequest(EmailMessage message, EmailSettings settings)
            throws EmailConfigurationException {
        Destination destination = Destination.builder()
                .toAddresses(message.getTo())
                .ccAddresses(message.getCc())
                .bccAddresses(message.getBcc())
                .build();

        Body.Builder body = Body.builder();
        if (EmailMessage.hasText(message.getTextBody())) {
            body.text(content(message.getTextBody()));
        }
        if (EmailMessage.hasText(message.getHtmlBody())) {
            body.html(content(message.getHtmlBody()));
        }

        software.amazon.awssdk.services.ses.model.Message sesMessage =
                software.amazon.awssdk.services.ses.model.Message.builder()
                        .subject(content(message.getSubject()))
                        .body(body.build())
                        .build();

        SendEmailRequest.Builder request = SendEmailRequest.builder()
                .source(formatAddress(settings.getFromAddress(), settings.getFromName()))
                .destination(destination)
                .message(sesMessage);

        if (EmailMessage.hasText(settings.getReplyTo())) {
            request.replyToAddresses(settings.getReplyTo());
        }
        if (EmailMessage.hasText(settings.getSesConfigurationSet())) {
            request.configurationSetName(settings.getSesConfigurationSet());
        }

        List<MessageTag> tags = buildTags(message.getMetadata());
        if (!tags.isEmpty()) {
            request.tags(tags);
        }

        return request.build();
    }

    private Content content(String value) {
        return Content.builder()
                .charset("UTF-8")
                .data(value)
                .build();
    }

    private String formatAddress(String address, String personal) throws EmailConfigurationException {
        if (!EmailMessage.hasText(personal)) {
            return address;
        }

        try {
            return new jakarta.mail.internet.InternetAddress(address, personal, "UTF-8").toString();
        } catch (UnsupportedEncodingException e) {
            throw new EmailConfigurationException("Email from name contains unsupported characters.");
        }
    }

    private List<MessageTag> buildTags(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }

        return metadata.entrySet().stream()
                .filter(entry -> isValidTagName(entry.getKey()) && entry.getValue() != null)
                .map(entry -> MessageTag.builder()
                        .name(entry.getKey())
                        .value(truncate(entry.getValue(), SES_TAG_VALUE_MAX_LENGTH))
                        .build())
                .toList();
    }

    private boolean isValidTagName(String name) {
        return name != null && name.matches("^[A-Za-z0-9_-]{1,256}$");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
