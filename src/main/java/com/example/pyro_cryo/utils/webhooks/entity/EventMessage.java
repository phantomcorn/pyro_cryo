package com.example.pyro_cryo.utils.webhooks.entity;

import com.example.pyro_cryo.entity.Status;
import com.example.pyro_cryo.utils.webhooks.InvoiceEventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Builder
@Table(name = "event_messages")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class EventMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "username", nullable = false)
    String username;

    @Column(name = "merchant_address", nullable = false)
    String merchantAddress;

    @Column(name = "invoice_id", nullable = false)
    String invoiceId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    Status status;

    @Builder.Default
    @Column(name = "is_sent")
    boolean isSent = false;

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
