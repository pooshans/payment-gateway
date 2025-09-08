package com.payment.gateway.repository;

import com.payment.gateway.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByEventId(String eventId);

    List<WebhookEvent> findByProcessedFalseOrderByReceivedAtAsc();

    List<WebhookEvent> findByEventTypeAndProcessed(String eventType, boolean processed);

    List<WebhookEvent> findByRelatedPaymentId(String paymentId);

    List<WebhookEvent> findByRelatedSubscriptionId(String subscriptionId);
}
