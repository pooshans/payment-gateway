package com.payment.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "TransactionEntity")
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currencyCode;

    @Column
    private String authorizationCode;

    @Column(nullable = false)
    private String status;

    @Column
    private String responseCode;

    @Column
    private String responseMessage;

    @Column
    private String cardType;

    @Column
    private String last4Digits;

    @Column(nullable = false)
    private boolean authorized;

    @Column(nullable = false)
    private boolean captured;

    @Column(nullable = false)
    private boolean refunded;

    @Column
    private boolean voided;

    @Column
    private BigDecimal refundedAmount;

    @Column(unique = true)
    private String idempotencyKey;

    @Column
    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
