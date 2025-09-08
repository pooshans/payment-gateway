package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String transactionId;
    private String authCode;
    private String status; // APPROVED, DECLINED, ERROR
    private String responseCode;
    private String responseMessage;
    private BigDecimal amount;
    private String currencyCode;
    private String cardType;
    private String last4Digits;
    private LocalDateTime transactionDate;
    private String correlationId; // For request tracing

    // For refunds and voids
    private String originalTransactionId;

    // For auth/capture
    private boolean isAuthorized;
    private boolean isCaptured;

    // For recurring payments
    private String subscriptionId;
}
