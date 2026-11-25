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

    List<EventMessage> eventMessages = List.of(
            EventMessage.builder()
                    .username("user1")
                    .merchantAddress("0x1111111111111111111111111111111111111111")
                    .invoiceId("b833631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                    .status(Status.DETECTED)
                    .isSent(false).build(),

            EventMessage.builder()
                    .username("user1")
                    .merchantAddress("0x1111111111111111111111111111111111111111")
                    .invoiceId("f933631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                    .status(Status.CONFIRMED)
                    .isSent(false).build(),

            EventMessage.builder()
                    .username("user1")
                    .merchantAddress("0x1111111111111111111111111111111111111111")
                    .invoiceId("b933631c3fae26d8eec0f0317f770095071dabb50ce4ea8e7ccafdd976ba187f")
                    .status(Status.REORG)
                    .isSent(false).build()
    );

    @BeforeEach
    public void insertAndFetchEvents() {
        eventMessageRepository.saveAll(eventMessages);
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
        for (int i = 0; i < n; i++) {
            executorService.submit(new EventMessageWorker(eventMessageService, merchantService, httpClient));
        }
        return executorService;
    }

    @Test
    public void itemExistsInOutbox() {
        Assertions.assertFalse(outbox.isEmpty());
    }

    @Test
    public void singleWorkerStandbyWheneverOutboxEmpty() throws InterruptedException {
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
    public void singleWorkerCanProcessOutbox() throws InterruptedException {
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


    @Test
    public void multiWorkerCanProcessOutbox() throws InterruptedException, ExecutionException, TimeoutException {
        int workerCount = 3;

        CountDownLatch latch = new CountDownLatch(workerCount);
        doAnswer(inv -> {
            inv.callRealMethod();
            latch.countDown();
            return null;
        }).doNothing().when(eventMessageService).markAsProcessed(eventMessages.get(0));
        doAnswer(inv -> {
            inv.callRealMethod();
            latch.countDown();
            return null;
        }).doNothing().when(eventMessageService).markAsProcessed(eventMessages.get(1));
        doAnswer(inv -> {
            inv.callRealMethod();
            latch.countDown();
            return null;
        }).doNothing().when(eventMessageService).markAsProcessed(eventMessages.get(2));
        ExecutorService executorService = spawnWorker(outbox.size());
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
        stop(executorService);
        verify(eventMessageService, times(workerCount)).markAsProcessed(any());
    }

}