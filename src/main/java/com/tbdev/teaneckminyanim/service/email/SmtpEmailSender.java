package com.tbdev.teaneckminyanim.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

@Component
public class SmtpEmailSender implements EmailSender {
    private static final int MAIL_TIMEOUT_MS = 10_000;

    @Override
    public EmailProvider getProvider() {
        return EmailProvider.SMTP;
    }

    @Override
    public void send(EmailMessage message, EmailSettings settings) throws EmailSendingException {
        JavaMailSenderImpl mailSender = buildMailSender(settings);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name());

            helper.setTo(toArray(message.getTo()));
            if (!message.getCc().isEmpty()) {
                helper.setCc(toArray(message.getCc()));
            }
            if (!message.getBcc().isEmpty()) {
                helper.setBcc(toArray(message.getBcc()));
            }

            helper.setSubject(message.getSubject());
            helper.setFrom(internetAddress(settings.getFromAddress(), settings.getFromName()));
            if (EmailMessage.hasText(settings.getReplyTo())) {
                helper.setReplyTo(settings.getReplyTo());
            }

            if (EmailMessage.hasText(message.getHtmlBody()) && EmailMessage.hasText(message.getTextBody())) {
                helper.setText(message.getTextBody(), message.getHtmlBody());
            } else if (EmailMessage.hasText(message.getHtmlBody())) {
                helper.setText(message.getHtmlBody(), true);
            } else {
                helper.setText(message.getTextBody(), false);
            }

            mailSender.send(mimeMessage);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            throw new EmailSendingException("SMTP email send failed: " + e.getMessage(), e);
        }
    }

    private JavaMailSenderImpl buildMailSender(EmailSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.getSmtpHost());
        mailSender.setPort(settings.getSmtpPort());

        if (EmailMessage.hasText(settings.getSmtpUsername())) {
            mailSender.setUsername(settings.getSmtpUsername());
        }
        if (EmailMessage.hasText(settings.getSmtpPassword())) {
            mailSender.setPassword(settings.getSmtpPassword());
        }

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(EmailMessage.hasText(settings.getSmtpUsername())));
        properties.put("mail.smtp.starttls.enable", String.valueOf(settings.isSmtpStartTlsEnabled()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(MAIL_TIMEOUT_MS));
        properties.put("mail.smtp.timeout", String.valueOf(MAIL_TIMEOUT_MS));
        properties.put("mail.smtp.writetimeout", String.valueOf(MAIL_TIMEOUT_MS));

        return mailSender;
    }

    private InternetAddress internetAddress(String address, String personal)
            throws MessagingException, UnsupportedEncodingException {
        if (EmailMessage.hasText(personal)) {
            return new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
        }
        return new InternetAddress(address);
    }

    private String[] toArray(List<String> values) {
        return values.toArray(String[]::new);
    }
}
