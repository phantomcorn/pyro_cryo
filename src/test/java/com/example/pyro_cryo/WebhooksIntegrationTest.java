package com.example.pyro_cryo;

import com.example.pyro_cryo.dto.MerchantDto;
import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.service.MerchantService;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageService;
import com.example.pyro_cryo.utils.webhooks.EventOutbox;
import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.EventSender;
import com.example.pyro_cryo.utils.webhooks.exceptions.RetryableWebhookException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest @ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@EnableRetry
@Transactional
public class WebhooksIntegrationTest {

    @Autowired
    EventOutbox inbox;

    @MockitoSpyBean
    MerchantService merchantService;

    @MockitoSpyBean
    EventSender sender;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    EventMessageService dispatcher;
    String merchantAddress = "0x1111111111111111111111111111111111111111";
    String merchantUsername = "user1";

    @BeforeEach
    public void buildDispatcher() {
        dispatcher = new EventMessageService(inbox);
        ReflectionTestUtils.setField(dispatcher, "merchantService", merchantService);
        ReflectionTestUtils.setField(dispatcher, "sender", sender);
    }

    @AfterEach
    public void unsubscribe() {
        dispatcher.dispose();
    }

    @Test
    public void dispatcherCanSeeMessageInbox() {
        EventMessage message = EventMessage
                .builder()
                .username("user1")
                .merchantAddress("0x1111111111111111111111111111111111111111")
                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
                .status(Status.DETECTED)
                .build();

        inbox.getPublisher().onNext(message);
        Mockito.verify(merchantService).findMerchantByUsername(message.getUsername());
    }

    @Test
    public void dispatcherHandlerSendsRequest() throws ExecutionException, InterruptedException {
        EventMessage message = EventMessage
                .builder()
                .username(merchantUsername)
                .merchantAddress(merchantAddress)
                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
                .status(Status.DETECTED)
                .build();

        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();

        inbox.getPublisher().onNext(message);
        Mockito.verify(sender).send(
                message.getInvoiceId(),
                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
                merchantDto
        );
    }

    @Test
    public void NewInvoiceDoesNotTriggerWebhook() throws ExecutionException, InterruptedException {
        EventMessage message = EventMessage
                .builder()
                .username(merchantUsername)
                .merchantAddress(merchantAddress)
                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
                .status(Status.NOT_PAID)
                .build();

        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();

        inbox.getPublisher().onNext(message);
        Mockito.verify(sender, Mockito.never()).send(
                message.getInvoiceId(),
                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
                merchantDto
        );
    }

    @Test
    @DirtiesContext
    public void failedWebhookWillRetry3Times() throws ExecutionException, InterruptedException {
        EventMessage message = EventMessage
                .builder()
                .username(merchantUsername)
                .merchantAddress(merchantAddress)
                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
                .status(Status.DETECTED)
                .build();

        MerchantDto merchantDto = merchantService.findMerchantByUsername(merchantUsername).get();
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);

        Mockito.when(httpResponse.statusCode()).thenReturn(429);
        Mockito.when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        inbox.getPublisher().onNext(message);
        Mockito.verify(sender, Mockito.timeout(4000).times(3)).send(
                message.getInvoiceId(),
                EventMessage.statusToInvoiceEventStatus(message.getStatus()),
                merchantDto
        );
    }

    @Test
    @DirtiesContext
    public void afterRetryingFailedWebhookTriggerFallback() {
        EventMessage message = EventMessage
                .builder()
                .username(merchantUsername)
                .merchantAddress(merchantAddress)
                .invoiceId(Hash.sha3String(UUID.randomUUID().toString()))
                .status(Status.DETECTED)
                .build();

        ReflectionTestUtils.setField(sender, "httpClient", httpClient);

        Mockito.when(httpResponse.statusCode()).thenReturn(429);
        Mockito.when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        inbox.getPublisher().onNext(message);
        Mockito.verify(sender).failedWebhooksFallback(any(RetryableWebhookException.class), eq(message));
    }
}
