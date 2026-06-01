package com.tbdev.teaneckminyanim.service.email;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class EmailMessage {
    @Singular("to")
    private final List<String> to;

    @Singular("cc")
    private final List<String> cc;

    @Singular("bcc")
    private final List<String> bcc;

    private final String subject;
    private final String textBody;
    private final String htmlBody;

    @Singular("metadata")
    private final Map<String, String> metadata;

    private final String organizationId;

    public int recipientCount() {
        return size(to) + size(cc) + size(bcc);
    }

    public void validate() throws EmailConfigurationException {
        if (recipientCount() == 0) {
            throw new EmailConfigurationException("Email must include at least one recipient.");
        }

        if (!hasText(subject)) {
            throw new EmailConfigurationException("Email subject cannot be empty.");
        }

        if (!hasText(textBody) && !hasText(htmlBody)) {
            throw new EmailConfigurationException("Email must include a plain text or HTML body.");
        }
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int size(List<String> values) {
        return values == null ? 0 : values.size();
    }
}
