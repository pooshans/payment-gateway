package com.payment.gateway.service.impl;

import com.payment.gateway.dto.TransactionDto;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.PaymentTransaction;
import com.payment.gateway.exception.ResourceNotFoundException;
import com.payment.gateway.repository.PaymentRepository;
import com.payment.gateway.repository.PaymentTransactionRepository;
import com.payment.gateway.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public TransactionDto createTransaction(TransactionDto transactionDto) {
        log.info("Creating transaction with payment ID: {}", transactionDto.getPaymentId());

        // Find the associated payment
        Payment payment = paymentRepository.findById(transactionDto.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with id: " + transactionDto.getPaymentId()));

        // Create and save the transaction
        PaymentTransaction transaction = mapToEntity(transactionDto, payment);
        PaymentTransaction savedTransaction = transactionRepository.save(transaction);

        log.info("Transaction created successfully with ID: {}", savedTransaction.getId());
        return mapToDto(savedTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionDto> getTransaction(Long id) {
        log.info("Getting transaction with ID: {}", id);
        return transactionRepository.findById(id).map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionDto> getTransactionByTransactionId(String transactionId) {
        log.info("Getting transaction with transaction ID: {}", transactionId);
        return transactionRepository.findByTransactionId(transactionId).map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByPayment(Long paymentId) {
        log.info("Getting transactions for payment with ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        return transactionRepository.findByPayment(payment).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByPaymentAndType(Long paymentId, String type) {
        log.info("Getting {} transactions for payment with ID: {}", type, paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        return transactionRepository.findByPaymentAndType(payment, type).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findByTransactionId(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId);
    }

    @Override
    @Transactional
    public PaymentTransaction saveTransaction(PaymentTransaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> findByPayment(Payment payment) {
        return transactionRepository.findByPayment(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> findByPaymentAndType(Payment payment, String type) {
        return transactionRepository.findByPaymentAndType(payment, type);
    }

    // Helper methods for mapping between entity and DTO
    private TransactionDto mapToDto(PaymentTransaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .transactionId(transaction.getTransactionId())
                .paymentId(transaction.getPayment().getId())
                .type(transaction.getType())
                .originalTransactionId(transaction.getOriginalTransactionId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .responseCode(transaction.getResponseCode())
                .responseMessage(transaction.getResponseMessage())
                .authCode(transaction.getAuthCode())
                .correlationId(transaction.getCorrelationId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private PaymentTransaction mapToEntity(TransactionDto dto, Payment payment) {
        return PaymentTransaction.builder()
                .payment(payment)
                .transactionId(dto.getTransactionId())
                .type(dto.getType())
                .originalTransactionId(dto.getOriginalTransactionId())
                .amount(dto.getAmount())
                .status(dto.getStatus())
                .responseCode(dto.getResponseCode())
                .responseMessage(dto.getResponseMessage())
                .authCode(dto.getAuthCode())
                .correlationId(dto.getCorrelationId())
                .build();
    }
}
