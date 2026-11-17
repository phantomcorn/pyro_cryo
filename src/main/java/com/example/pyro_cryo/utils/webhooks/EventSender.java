package com.example.pyro_cryo.utils.webhooks;

import com.example.pyro_cryo.dto.MerchantDto;
import com.example.pyro_cryo.utils.HMacUtil;
import com.example.pyro_cryo.utils.webhooks.exceptions.NonRetryableWebhookException;
import com.example.pyro_cryo.utils.webhooks.exceptions.RetryableWebhookException;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Component
public class EventSender {

    private final HttpClient httpClient;

    EventSender() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void send(String invoiceId, InvoiceEventStatus invoiceEventStatus, MerchantDto merchantDto) {
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

    public HttpRequest webhookRequest(String invoiceId, InvoiceEventStatus status, MerchantDto merchantDto) {
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

    public void failedWebhooksFallback(RetryableWebhookException e, EventMessage message) {
        System.out.println("Failed Webhooks " + e.toString() + " " + message);
    }
}
