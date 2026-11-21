package com.example.pyro_cryo.utils.webhooks.service;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.repository.EventMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
@Profile("!test")
@EnableAsync
public class EventMessageService {

    @Autowired
    EventMessageRepository eventMessageRepository;

    Deque<EventMessage> eventOutbox = new ArrayDeque<>();

    @Async
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void processEventMessage() {
        List<EventMessage> needSend = eventMessageRepository.findByIsSent(false);
        addAll(needSend);
    }



    public void add(EventMessage eventMessage) {
        eventOutbox.add(eventMessage);
    }

    public void addAll(List<EventMessage> eventMessages) {
        eventOutbox.addAll(eventMessages);
    }

    public EventMessage pop() {
        return eventOutbox.pop();
    }



}
