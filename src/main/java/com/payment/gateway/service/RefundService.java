package com.payment.gateway.service;

import com.payment.gateway.dto.RefundDto;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.RefundTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RefundService {

    // Create a new refund
    RefundDto createRefund(RefundDto refundDto);

    // Get a refund by ID
    Optional<RefundDto> getRefund(Long id);

    // Get a refund by refund ID
    Optional<RefundDto> getRefundByRefundId(String refundId);

    // Get all refunds for a payment
    List<RefundDto> getRefundsByPayment(Long paymentId);

    // Get all refunds for an original transaction
    List<RefundDto> getRefundsByOriginalTransaction(String originalTransactionId);

    // Calculate total refunded amount for a payment
    BigDecimal getTotalRefundedAmount(Long paymentId);

    // For internal use
    Optional<RefundTransaction> findByRefundId(String refundId);

    Optional<RefundTransaction> findByIdempotencyKey(String idempotencyKey);

    RefundTransaction saveRefund(RefundTransaction refund);

    List<RefundTransaction> findByPayment(Payment payment);
}
