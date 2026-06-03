package com.tbdev.teaneckminyanim.service.subscription;

public record SubscriptionRequest(
        String name,
        String email,
        String honeypot,
        String list,
        String subform
) {
}
