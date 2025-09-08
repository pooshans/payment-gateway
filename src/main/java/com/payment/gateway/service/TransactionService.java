package com.payment.gateway.service;

import com.payment.gateway.dto.TransactionDto;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.PaymentTransaction;

import java.util.List;
import java.util.Optional;

public interface TransactionService {

    // Create a new transaction
    TransactionDto createTransaction(TransactionDto transactionDto);

    // Get a transaction by ID
    Optional<TransactionDto> getTransaction(Long id);

    // Get a transaction by gateway transaction ID
    Optional<TransactionDto> getTransactionByTransactionId(String transactionId);

    // Get all transactions for a payment
    List<TransactionDto> getTransactionsByPayment(Long paymentId);

    // Get all transactions of a specific type for a payment
    List<TransactionDto> getTransactionsByPaymentAndType(Long paymentId, String type);

    // For internal use
    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    PaymentTransaction saveTransaction(PaymentTransaction transaction);

    List<PaymentTransaction> findByPayment(Payment payment);

    List<PaymentTransaction> findByPaymentAndType(Payment payment, String type);
}
