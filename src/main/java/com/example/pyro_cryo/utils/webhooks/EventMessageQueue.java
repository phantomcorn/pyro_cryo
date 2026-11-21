package com.example.pyro_cryo.utils.webhooks;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class EventMessageQueue {

    Deque<EventMessage> eventOutbox = new ArrayDeque<>();

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
