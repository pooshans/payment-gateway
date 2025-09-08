package com.payment.gateway.repository;

import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByCustomer(Customer customer);

    Optional<PaymentMethod> findByCustomerAndIsDefaultTrue(Customer customer);

    Optional<PaymentMethod> findByPaymentProfileId(String paymentProfileId);
}
