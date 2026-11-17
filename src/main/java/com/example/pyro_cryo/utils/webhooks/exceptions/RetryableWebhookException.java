package com.example.pyro_cryo.utils.webhooks.exceptions;

public class RetryableWebhookException extends RuntimeException {
    public RetryableWebhookException(String s) {
        super(s);
    }
}
