package com.payment.gateway.repository;

import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByCustomer(Customer customer);

    Page<Payment> findByCustomer(Customer customer, Pageable pageable);

    Optional<Payment> findBySubscriptionId(String subscriptionId);
}
