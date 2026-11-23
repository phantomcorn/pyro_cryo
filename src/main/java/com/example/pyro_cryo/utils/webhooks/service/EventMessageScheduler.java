package com.example.pyro_cryo.utils.webhooks.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableAsync
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
public class EventMessageScheduler {

    @Autowired
    EventMessageService eventMessageService;

    @Async
    @Scheduled(fixedRate = 30000)
    public void run() {
        eventMessageService.fetchEvents();
    }
}
