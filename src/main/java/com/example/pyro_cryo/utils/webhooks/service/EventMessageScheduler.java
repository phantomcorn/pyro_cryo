package com.example.pyro_cryo.utils.webhooks.service;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@EnableAsync
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
public class EventMessageScheduler {

    @Autowired
    EventMessageService eventMessageService;

    @Async
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void fetchEvents() {
        List<EventMessage> needSend = eventMessageService.findAll();
        eventMessageService.addAll(needSend);
    }
}
