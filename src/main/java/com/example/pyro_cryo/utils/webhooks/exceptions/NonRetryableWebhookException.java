package com.example.pyro_cryo.utils.webhooks.exceptions;

public class NonRetryableWebhookException extends RuntimeException {
    public NonRetryableWebhookException(String s) {
        super(s);
    }
}
