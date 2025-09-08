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
public class RefundDto {

    private Long id;
    private String refundId;
    private String originalTransactionId;
    private Long paymentId;
    private BigDecimal amount;
    private String status;
    private String responseCode;
    private String responseMessage;
    private String reason;
    private String correlationId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
