package com.example.pyro_cryo;

import com.example.pyro_cryo.service.MerchantService;
import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageScheduler;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageService;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageWorker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest @ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@EnableRetry
@Transactional
public class WebhooksIntegrationTest {

    @Autowired
    EventMessageWorker inbox;

    @MockitoSpyBean
    MerchantService merchantService;

    @MockitoSpyBean
    EventMessageService eventMessageService;

    @Autowired
    EventMessageService outbox;
    String merchantAddress = "0x1111111111111111111111111111111111111111";
    String merchantUsername = "user1";

    @BeforeEach
    public void fetchEvents() {
        List<EventMessage> needSend = eventMessageService.findAll();
        eventMessageService.addAll(needSend);
    }

    @Test
    public void itemExistsInOutbox() {
        Assertions.assertFalse(outbox.isEmpty());
    }
//    @Test
//    public void dispatcherCanSeeMessageInbox() {
//        EventMessage message = EventMessage
//                .builder()
//                .username("user1")
//                .merchantAddress("0x1111111111111111111111111111111111111111")
//                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
//                .status(Status.DETECTED)
//                .build();
//
//        inbox.getPublisher().onNext(message);
//        Mockito.verify(merchantService).findMerchantByUsername(message.getUsername());
//    }
//
//    @Test
//    public void dispatcherHandlerSendsRequest() throws ExecutionException, InterruptedException {
//        EventMessage message = EventMessage
//                .builder()
//                .username(merchantUsername)
//                .merchantAddress(merchantAddress)
//                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
//                .status(Status.DETECTED)
//                .build();
//
//        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();
//
//        inbox.getPublisher().onNext(message);
//        Mockito.verify(sender).send(
//                message.getInvoiceId(),
//                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
//                merchantDto
//        );
//    }
//
//    @Test
//    public void NewInvoiceDoesNotTriggerWebhook() throws ExecutionException, InterruptedException {
//        EventMessage message = EventMessage
//                .builder()
//                .username(merchantUsername)
//                .merchantAddress(merchantAddress)
//                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
//                .status(Status.NOT_PAID)
//                .build();
//
//        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();
//
//        inbox.getPublisher().onNext(message);
//        Mockito.verify(sender, Mockito.never()).send(
//                message.getInvoiceId(),
//                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
//                merchantDto
//        );
//    }
//
//    @Test
//    @DirtiesContext
//    public void failedWebhookWillRetry3Times() throws ExecutionException, InterruptedException {
//        EventMessage message = EventMessage
//                .builder()
//                .username(merchantUsername)
//                .merchantAddress(merchantAddress)
//                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
//                .status(Status.DETECTED)
//                .build();
//
//        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();
//        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
//
//        Mockito.when(httpResponse.statusCode()).thenReturn(429);
//        Mockito.when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(httpResponse));
//        inbox.getPublisher().onNext(message);
//        Mockito.verify(sender, Mockito.timeout(4000).times(3)).send(
//                message.getInvoiceId(),
//                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
//                merchantDto
//        );
//    }
//
//    @Test
//    @DirtiesContext
//    public void afterRetryingFailedWebhookTriggerFallback() {
//        EventMessage message = EventMessage
//                .builder()
//                .username(merchantUsername)
//                .merchantAddress(merchantAddress)
//                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
//                .status(Status.DETECTED)
//                .build();
//
//        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
//
//        Mockito.when(httpResponse.statusCode()).thenReturn(429);
//        Mockito.when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(httpResponse));
//        inbox.getPublisher().onNext(message);
//        Mockito.verify(sender).failedWebhooksFallback(any(RetryableWebhookException.class), eq(message));
//    }
}
