package com.payment.gateway.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "PaymentTransaction")
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId; // Authorize.Net transaction ID

    @Column(nullable = true)
    private String originalTransactionId; // For refunds/voids

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String status; // AUTHORIZED, CAPTURED, SETTLED, VOIDED, REFUNDED, FAILED

    @Column(nullable = true)
    private String authCode;

    @Column(nullable = false)
    private String responseCode;

    @Column
    private String responseMessage;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(length = 20)
    private String cardType;

    @Column(length = 4)
    private String last4Digits;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // Transaction type fields
    @Column(name = "is_authorization")
    private boolean authorization;
    private boolean capture;
    private boolean refund;
    private boolean voidTx;
    private boolean subscription;

    // Subscription fields
    private String subscriptionId;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
