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
public class TransactionDto {

    private Long id;
    private String transactionId;
    private Long paymentId;
    private String type;
    private String originalTransactionId;
    private BigDecimal amount;
    private String status;
    private String responseCode;
    private String responseMessage;
    private String authCode;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
