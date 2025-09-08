package com.payment.gateway.repository;

import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findBySubscriptionId(String subscriptionId);

    List<Subscription> findByCustomer(Customer customer);

    List<Subscription> findByCustomerAndStatus(Customer customer, String status);
}
