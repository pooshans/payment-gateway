package com.payment.gateway.service.impl;

import com.payment.gateway.dto.RefundDto;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.RefundTransaction;
import com.payment.gateway.exception.ResourceNotFoundException;
import com.payment.gateway.repository.PaymentRepository;
import com.payment.gateway.repository.RefundTransactionRepository;
import com.payment.gateway.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundTransactionRepository refundRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public RefundDto createRefund(RefundDto refundDto) {
        log.info("Creating refund for payment with ID: {}", refundDto.getPaymentId());

        // Check for idempotency
        if (refundDto.getIdempotencyKey() != null) {
            Optional<RefundTransaction> existingRefund = refundRepository
                    .findByIdempotencyKey(refundDto.getIdempotencyKey());
            if (existingRefund.isPresent()) {
                log.info("Refund already exists for idempotency key: {}", refundDto.getIdempotencyKey());
                return mapToDto(existingRefund.get());
            }
        }

        // Find the associated payment
        Payment payment = paymentRepository.findById(refundDto.getPaymentId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Payment not found with id: " + refundDto.getPaymentId()));

        // Create and save the refund
        RefundTransaction refund = mapToEntity(refundDto, payment);
        RefundTransaction savedRefund = refundRepository.save(refund);

        log.info("Refund created successfully with ID: {}", savedRefund.getId());
        return mapToDto(savedRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundDto> getRefund(Long id) {
        log.info("Getting refund with ID: {}", id);
        return refundRepository.findById(id).map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundDto> getRefundByRefundId(String refundId) {
        log.info("Getting refund with refund ID: {}", refundId);
        return refundRepository.findByRefundId(refundId).map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDto> getRefundsByPayment(Long paymentId) {
        log.info("Getting refunds for payment with ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        return refundRepository.findByPayment(payment).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDto> getRefundsByOriginalTransaction(String originalTransactionId) {
        log.info("Getting refunds for original transaction ID: {}", originalTransactionId);

        return refundRepository.findByOriginalTransactionId(originalTransactionId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRefundedAmount(Long paymentId) {
        log.info("Calculating total refunded amount for payment with ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        return refundRepository.findByPayment(payment).stream()
                .filter(refund -> "COMPLETED".equals(refund.getStatus()))
                .map(RefundTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundTransaction> findByRefundId(String refundId) {
        return refundRepository.findByRefundId(refundId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundTransaction> findByIdempotencyKey(String idempotencyKey) {
        return refundRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    @Transactional
    public RefundTransaction saveRefund(RefundTransaction refund) {
        return refundRepository.save(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundTransaction> findByPayment(Payment payment) {
        return refundRepository.findByPayment(payment);
    }

    // Helper methods for mapping between entity and DTO
    private RefundDto mapToDto(RefundTransaction refund) {
        return RefundDto.builder()
                .id(refund.getId())
                .refundId(refund.getRefundId())
                .originalTransactionId(refund.getOriginalTransactionId())
                .paymentId(refund.getPayment().getId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .responseCode(refund.getResponseCode())
                .responseMessage(refund.getResponseMessage())
                .reason(refund.getReason())
                .correlationId(refund.getCorrelationId())
                .idempotencyKey(refund.getIdempotencyKey())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }

    private RefundTransaction mapToEntity(RefundDto dto, Payment payment) {
        return RefundTransaction.builder()
                .refundId(dto.getRefundId())
                .originalTransactionId(dto.getOriginalTransactionId())
                .payment(payment)
                .amount(dto.getAmount())
                .status(dto.getStatus())
                .responseCode(dto.getResponseCode())
                .responseMessage(dto.getResponseMessage())
                .reason(dto.getReason())
                .correlationId(dto.getCorrelationId())
                .idempotencyKey(dto.getIdempotencyKey())
                .build();
    }
}
