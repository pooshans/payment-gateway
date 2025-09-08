package com.payment.gateway.repository;

import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByPayment(Payment payment);

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    List<PaymentTransaction> findByPaymentAndType(Payment payment, String type);

    Optional<PaymentTransaction> findByOriginalTransactionId(String originalTransactionId);
}
