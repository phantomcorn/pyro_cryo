package com.example.pyro_cryo.utils.webhooks.service;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import com.example.pyro_cryo.utils.webhooks.repository.EventMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class EventMessageService {

    @Autowired
    EventMessageRepository eventMessageRepository;

    BlockingQueue<EventMessage> eventOutbox = new LinkedBlockingQueue<>();

    @Transactional
    public void fetchEvents() {
        List<EventMessage> needSend = eventMessageRepository.findByIsSent(false);
        addAll(needSend);
    }

    public void addAll(List<EventMessage> eventMessages) {
        eventOutbox.addAll(eventMessages);
    }

    public EventMessage take() throws InterruptedException {
        return eventOutbox.take();
    }

    public void markAsProcessed(EventMessage message) {
        message.setSent(true);
        eventMessageRepository.save(message);
    }

    public boolean isEmpty() {
        return eventOutbox.isEmpty();
    }

}
