package com.tbdev.teaneckminyanim.service.recaptcha;

public class RecaptchaVerificationException extends Exception {
    public RecaptchaVerificationException(String message) {
        super(message);
    }

    public RecaptchaVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
