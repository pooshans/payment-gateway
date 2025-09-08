package com.payment.gateway.repository;

import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.RefundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, Long> {

    List<RefundTransaction> findByPayment(Payment payment);

    Optional<RefundTransaction> findByRefundId(String refundId);

    Optional<RefundTransaction> findByIdempotencyKey(String idempotencyKey);

    List<RefundTransaction> findByOriginalTransactionId(String originalTransactionId);
}
