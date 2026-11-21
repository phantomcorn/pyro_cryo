package com.example.pyro_cryo.utils.webhooks.service;

import com.example.pyro_cryo.dto.MerchantDto;
import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.service.MerchantService;
import com.example.pyro_cryo.utils.webhooks.EventOutbox;
import com.example.pyro_cryo.utils.webhooks.EventSender;
import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.exceptions.RetryableWebhookException;
import com.example.pyro_cryo.utils.webhooks.repository.EventMessageRepository;
import io.reactivex.disposables.Disposable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@Profile("!test")
@EnableAsync
public class EventMessageService { //subscriber to EventInbox

    @Autowired
    MerchantService merchantService;

    @Autowired
    EventMessageRepository eventMessageRepository;

    @Autowired
    EventSender sender;

    Disposable disposable;

    RetryTemplate retryTemplate;

    public EventMessageService(EventOutbox inbox) {
        disposable = inbox.getPublisher().subscribe(this::handler, this::onError);
        retryTemplate = RetryTemplate.builder()
                .maxAttempts(3).retryOn(RetryableWebhookException.class)
                .exponentialBackoff(1000,2, 5000, true)
                .build();
    }

    @Scheduled(fixedDelayString = "30000")
    public void processEventMessage() {

    }

    public void handler(EventMessage message) {
        if (message.getStatus().equals(Status.NOT_PAID)) {
            return; //we don't need to send webhook for when invoice is created
        }

        //find merchant uri
        MerchantDto merchantDto = merchantService.findMerchantByUsername(message.getUsername()).orElseThrow(NoSuchElementException::new);

        retryTemplate.execute(
                ctx -> { //try 3 times
                    sender.send(message.getInvoiceId(), EventMessage.statusToInvoiceEventStatus(message.getStatus()), merchantDto);
                    return null;
                },
                ctx -> { //fallback
                    sender.failedWebhooksFallback(ctx.getLastThrowable(),message);
                    return null;
                }
        );
    }

    public void onError(Throwable err) {
        err.printStackTrace();
    }

    public void dispose() {
        disposable.dispose();
    }

}
