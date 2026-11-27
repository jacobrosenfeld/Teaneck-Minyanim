package com.tbdev.teaneckminyanim.service;

/** The calendar event exists but is not a MANUAL override (it is generated from rules or imported). */
public class NotManualOverrideException extends RuntimeException {
    public NotManualOverrideException(String message) {
        super(message);
    }
}
