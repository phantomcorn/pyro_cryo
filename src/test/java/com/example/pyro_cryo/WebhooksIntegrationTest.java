package com.example.pyro_cryo;

import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.service.MerchantService;
import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.repository.EventMessageRepository;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageService;
import com.example.pyro_cryo.utils.webhooks.service.EventMessageWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@EnableRetry
public class WebhooksIntegrationTest {

    @MockitoSpyBean
    MerchantService merchantService;

    @MockitoSpyBean
    EventMessageService eventMessageService;

    @Autowired
    EventMessageRepository eventMessageRepository;

    @Autowired
    EventMessageService outbox;

    String merchantAddress = "0x1111111111111111111111111111111111111111";
    String merchantUsername = "user1";

    @BeforeEach
    public void insertAndFetchEvents() {
        EventMessage eventMessage = EventMessage.builder()
                .username("user1")
                .merchantAddress("0x1111111111111111111111111111111111111111")
                .invoiceId("b833631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                .status(Status.DETECTED)
                .isSent(false).build();

        EventMessage eventMessage1 = EventMessage.builder()
                .username("user1")
                .merchantAddress("0x1111111111111111111111111111111111111111")
                .invoiceId("f933631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                .status(Status.CONFIRMED)
                .isSent(false).build();

        EventMessage eventMessage2 = EventMessage.builder()
                .username("user1")
                .merchantAddress("0x1111111111111111111111111111111111111111")
                .invoiceId("b933631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                .status(Status.REORG)
                .isSent(false).build();

        eventMessageRepository.saveAll(List.of(eventMessage, eventMessage1, eventMessage2));
        eventMessageService.fetchEvents();
    }

    @AfterEach
    public void resetData() {
        eventMessageRepository.deleteAll();
    }

    public void stop(ExecutorService executorService) throws InterruptedException {
        executorService.shutdownNow();
        assertTrue(executorService.awaitTermination(2, TimeUnit.SECONDS));
    }

    public ExecutorService spawnWorker(int n) {

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200); //all webhooks sent correct
        when(httpClient.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        ExecutorService executorService = Executors.newFixedThreadPool(n);
        executorService.submit(new EventMessageWorker(eventMessageService, merchantService, httpClient));
        return executorService;
    }

    @Test
    public void itemExistsInOutbox() {
        Assertions.assertFalse(outbox.isEmpty());
    }

    @Test
    public void singleWorkerStandbyWheneverOutboxEmpty() throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch latch = new CountDownLatch(outbox.size());
        doAnswer(inv -> {
            inv.callRealMethod();
            latch.countDown();
            return null;
        }).when(eventMessageService).markAsProcessed(any());
        ExecutorService executorService = spawnWorker(1);
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assertions.assertTrue(outbox.isEmpty());
        Assertions.assertFalse(executorService.isTerminated());
        stop(executorService);
    }

    @Test
    public void singleWorkerCanProcessOutbox() throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch latch = new CountDownLatch(outbox.size());
        doAnswer(inv -> {
            inv.callRealMethod();
            latch.countDown();
            return null;
        }).when(eventMessageService).markAsProcessed(any());
        ExecutorService executorService = spawnWorker(1);
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
        stop(executorService);
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
