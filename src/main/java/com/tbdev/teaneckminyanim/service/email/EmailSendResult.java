package com.tbdev.teaneckminyanim.service.email;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailSendResult {
    private final boolean success;
    private final EmailProvider provider;
    private final String message;

    public static EmailSendResult success(EmailProvider provider, String message) {
        return new EmailSendResult(true, provider, message);
    }

    public static EmailSendResult failure(EmailProvider provider, String message) {
        return new EmailSendResult(false, provider, message);
    }
}
