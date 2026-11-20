package com.example.pyro_cryo.utils.webhooks.entity;

import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.utils.webhooks.InvoiceEventStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Entity(name = "event_messages")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class EventMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String username;
    String merchantAddress;
    String invoiceId;
    Status status;

    public static InvoiceEventStatus statusToInvoiceEventStatus(Status status) {

        InvoiceEventStatus res = null;

        switch (status) {
            case NOT_PAID -> res = InvoiceEventStatus.NOT_PAID;
            case DETECTED ->  res = InvoiceEventStatus.PENDING;
            case REORG -> res = InvoiceEventStatus.REVERTED;
            case CONFIRMED -> res = InvoiceEventStatus.PAID;
            case EXPIRED -> res = InvoiceEventStatus.EXPIRED;
        }
        return res;
    }
}
