package com.example.pyro_cryo.utils.webhooks;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import io.reactivex.processors.PublishProcessor;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class EventOutbox {

    @Getter
    private final PublishProcessor<EventMessage> publisher;

    public EventOutbox() {
        publisher = PublishProcessor.create();
    }

}
