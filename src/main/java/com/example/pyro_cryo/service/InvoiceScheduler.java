package com.example.pyro_cryo.service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
public class InvoiceScheduler {

    @Autowired
    InvoiceService invoiceService;

    @Scheduled(fixedDelay = 30000)
    public boolean run() {
        return invoiceService.expireInvoice();
    }
}
