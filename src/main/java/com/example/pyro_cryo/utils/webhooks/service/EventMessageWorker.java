package com.example.pyro_cryo.utils.webhooks.service;

import com.example.pyro_cryo.dto.MerchantDto;
import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.service.MerchantService;
import com.example.pyro_cryo.utils.HMacUtil;
import com.example.pyro_cryo.utils.webhooks.InvoiceEventStatus;
import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.exceptions.NonRetryableWebhookException;
import com.example.pyro_cryo.utils.webhooks.exceptions.RetryableWebhookException;
import io.vertx.core.json.JsonObject;
import org.springframework.retry.support.RetryTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;

public class EventMessageWorker implements Runnable {

    private final EventMessageService outbox;
    private final MerchantService merchantService;
    private final RetryTemplate retryTemplate;
    private final HttpClient httpClient;
    private volatile boolean running = true;

    public EventMessageWorker(EventMessageService outbox, MerchantService merchantService, HttpClient httpClient) {
        this.outbox = outbox;
        this.merchantService = merchantService;
        this.httpClient = httpClient;
        retryTemplate = RetryTemplate.builder()
                .maxAttempts(3).retryOn(RetryableWebhookException.class)
                .exponentialBackoff(1000,2, 5000, true)
                .build();
    }

    @Override
    public void run() {
        while (running) {
            try {
                EventMessage message = outbox.take();
                handler(message);
            } catch (InterruptedException e) {
                running = false;
            }

        }
    }

    public void handler(EventMessage message) {

        if (message.getStatus().equals(Status.NOT_PAID)) {
            return; //we don't need to send webhook for when invoice is created
        }

        //find merchant uri
        MerchantDto merchantDto = merchantService.findMerchantByUsername(message.getUsername()).orElseThrow(NoSuchElementException::new);

        retryTemplate.execute(
                ctx -> { //try 3 times
                    send(message.getInvoiceId(), EventMessage.statusToInvoiceEventStatus(message.getStatus()), merchantDto);
                    outbox.markAsProcessed(message);
                    return null;
                },
                ctx -> { //fallback
                    failedWebhooksFallback(ctx.getLastThrowable(),message);
                    return null;
                }
        );
    }

    private void send(String invoiceId, InvoiceEventStatus invoiceEventStatus, MerchantDto merchantDto) {
        HttpRequest request = webhookRequest(invoiceId, invoiceEventStatus, merchantDto);
        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .thenAccept(code -> {
                        if (code == 429 || code >= 500) {
                            throw new RetryableWebhookException("Temporary webhook failure: " + code);
                        } else if (code >= 400) {
                            throw new NonRetryableWebhookException("Permanent webhook error: " + code);
                        }
                    })
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeEx) {
                throw runtimeEx; // Rethrows RetryableWebhookException or NonRetryableWebhookException directly
            }
            throw e;
        }
    }

    public void stop() {
        running = false;
    }

    private HttpRequest webhookRequest(String invoiceId, InvoiceEventStatus status, MerchantDto merchantDto) {
        JsonObject body = new JsonObject();
        body.put("invoiceId", invoiceId);
        body.put("status", status.name());
        if (status.equals(InvoiceEventStatus.PAID)) {
            body.put("success_url", merchantDto.successUrl().replace("{invoiceId}", invoiceId));
        }

        Map<String, Object> jsonPayload = body.getMap();
        String signature = HMacUtil.sign(merchantDto.secretKey(), jsonPayload);
        body.put("signature", signature);

        return HttpRequest.newBuilder()
                .uri(URI.create(merchantDto.uri()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private void failedWebhooksFallback(Throwable e, EventMessage message) {
        System.out.println("Failed Webhooks " + e.toString() + " " + message);
    }
}
