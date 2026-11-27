package com.tbdev.teaneckminyanim.service;

/** A manual-override target (a calendar event or a shul) does not exist, or belongs to another shul. */
public class OverrideNotFoundException extends RuntimeException {
    public OverrideNotFoundException(String message) {
        super(message);
    }
}
