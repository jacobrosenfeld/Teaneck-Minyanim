package com.tbdev.teaneckminyanim.service.email;

public interface EmailSender {
    EmailProvider getProvider();

    void send(EmailMessage message, EmailSettings settings)
            throws EmailConfigurationException, EmailSendingException;
}
