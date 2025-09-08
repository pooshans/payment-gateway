package com.payment.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "idx_webhook_event_id", columnList = "event_id"),
        @Index(name = "idx_webhook_event_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_source")
    private String eventSource;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature")
    private String signature;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "processed")
    private Boolean processed;

    @Column(name = "processing_attempts")
    private Integer processingAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "related_payment_id")
    private String relatedPaymentId;

    @Column(name = "related_subscription_id")
    private String relatedSubscriptionId;

    @Column(name = "related_customer_id")
    private Long relatedCustomerId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = LocalDateTime.now();
        this.processingAttempts = 0;
        this.processed = false;
    }
}
