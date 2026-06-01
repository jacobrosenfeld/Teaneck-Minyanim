package com.tbdev.teaneckminyanim.service.email;

import java.util.Locale;
import java.util.Optional;

public enum EmailProvider {
    SMTP,
    SES;

    public static Optional<EmailProvider> fromSetting(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(EmailProvider.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
