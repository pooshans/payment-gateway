package com.payment.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.entity.WebhookEvent;
import com.payment.gateway.service.PaymentService;
import com.payment.gateway.service.SubscriptionService;
import com.payment.gateway.service.WebhookProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessorImpl implements WebhookProcessor {

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public boolean processWebhookEvent(WebhookEvent webhookEvent) {
        log.info("Processing webhook event: {}, type: {}", webhookEvent.getEventId(), webhookEvent.getEventType());

        try {
            // Process based on event type
            switch (webhookEvent.getEventType()) {
                case "net.authorize.payment.authcapture.created":
                    return processPaymentAuthCaptureEvent(webhookEvent);
                case "net.authorize.payment.refund.created":
                    return processRefundEvent(webhookEvent);
                case "net.authorize.customer.subscription.created":
                case "net.authorize.customer.subscription.updated":
                case "net.authorize.customer.subscription.suspended":
                case "net.authorize.customer.subscription.terminated":
                case "net.authorize.customer.subscription.expiring":
                case "net.authorize.customer.subscription.expired":
                    return processSubscriptionEvent(webhookEvent);
                default:
                    log.warn("Unhandled webhook event type: {}", webhookEvent.getEventType());
                    return false;
            }
        } catch (Exception e) {
            log.error("Error processing webhook event {}", webhookEvent.getEventId(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public boolean processPaymentAuthCaptureEvent(WebhookEvent webhookEvent) {
        log.info("Processing payment auth capture event: {}", webhookEvent.getEventId());

        try {
            // Parse payload
            Map<String, Object> payload = objectMapper.readValue(webhookEvent.getPayload(), Map.class);

            // Extract payment ID and transaction ID
            String paymentId = extractPaymentId(payload);
            String transactionId = extractTransactionId(payload);

            if (paymentId == null || transactionId == null) {
                log.warn("Missing payment or transaction ID in webhook payload: {}", webhookEvent.getEventId());
                return false;
            }

            // Update payment status in the system
            log.info("Updating payment status for payment ID: {}, transaction ID: {}", paymentId, transactionId);
            // paymentService.updatePaymentStatusFromWebhook(paymentId, transactionId,
            // "CAPTURED", webhookEvent.getCorrelationId());

            // Mark as processed
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());

            return true;
        } catch (JsonProcessingException e) {
            log.error("Error parsing webhook payload for event {}", webhookEvent.getEventId(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public boolean processRefundEvent(WebhookEvent webhookEvent) {
        log.info("Processing refund event: {}", webhookEvent.getEventId());

        try {
            // Parse payload
            Map<String, Object> payload = objectMapper.readValue(webhookEvent.getPayload(), Map.class);

            // Extract payment ID and refund ID
            String paymentId = extractPaymentId(payload);
            String refundId = extractRefundId(payload);

            if (paymentId == null || refundId == null) {
                log.warn("Missing payment or refund ID in webhook payload: {}", webhookEvent.getEventId());
                return false;
            }

            // Update refund status in the system
            log.info("Updating refund status for payment ID: {}, refund ID: {}", paymentId, refundId);
            // paymentService.updateRefundStatusFromWebhook(paymentId, refundId,
            // "COMPLETED", webhookEvent.getCorrelationId());

            // Mark as processed
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());

            return true;
        } catch (JsonProcessingException e) {
            log.error("Error parsing webhook payload for event {}", webhookEvent.getEventId(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public boolean processSubscriptionEvent(WebhookEvent webhookEvent) {
        log.info("Processing subscription event: {}, type: {}",
                webhookEvent.getEventId(), webhookEvent.getEventType());

        try {
            // Parse payload
            Map<String, Object> payload = objectMapper.readValue(webhookEvent.getPayload(), Map.class);

            // Extract subscription ID
            String subscriptionId = extractSubscriptionId(payload);

            if (subscriptionId == null) {
                log.warn("Missing subscription ID in webhook payload: {}", webhookEvent.getEventId());
                return false;
            }

            // Process based on event subtype
            switch (webhookEvent.getEventType()) {
                case "net.authorize.customer.subscription.created":
                    log.info("Subscription created: {}", subscriptionId);
                    // subscriptionService.updateSubscriptionStatusFromWebhook(subscriptionId,
                    // "ACTIVE", webhookEvent.getCorrelationId());
                    break;

                case "net.authorize.customer.subscription.updated":
                    log.info("Subscription updated: {}", subscriptionId);
                    // subscriptionService.syncSubscriptionDetails(subscriptionId,
                    // webhookEvent.getCorrelationId());
                    break;

                case "net.authorize.customer.subscription.suspended":
                    log.info("Subscription suspended: {}", subscriptionId);
                    // subscriptionService.updateSubscriptionStatusFromWebhook(subscriptionId,
                    // "SUSPENDED", webhookEvent.getCorrelationId());
                    break;

                case "net.authorize.customer.subscription.terminated":
                    log.info("Subscription terminated: {}", subscriptionId);
                    // subscriptionService.updateSubscriptionStatusFromWebhook(subscriptionId,
                    // "CANCELLED", webhookEvent.getCorrelationId());
                    break;

                case "net.authorize.customer.subscription.expiring":
                    log.info("Subscription expiring: {}", subscriptionId);
                    // Notify about expiring subscription
                    break;

                case "net.authorize.customer.subscription.expired":
                    log.info("Subscription expired: {}", subscriptionId);
                    // subscriptionService.updateSubscriptionStatusFromWebhook(subscriptionId,
                    // "EXPIRED", webhookEvent.getCorrelationId());
                    break;
            }

            // Mark as processed
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());

            return true;
        } catch (JsonProcessingException e) {
            log.error("Error parsing webhook payload for event {}", webhookEvent.getEventId(), e);
            return false;
        }
    }

    private String extractPaymentId(Map<String, Object> payload) {
        if (payload.containsKey("id")) {
            return payload.get("id").toString();
        }
        return null;
    }

    private String extractTransactionId(Map<String, Object> payload) {
        if (payload.containsKey("transId")) {
            return payload.get("transId").toString();
        }
        return null;
    }

    private String extractRefundId(Map<String, Object> payload) {
        if (payload.containsKey("refTransId")) {
            return payload.get("refTransId").toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractSubscriptionId(Map<String, Object> payload) {
        if (payload.containsKey("subscription") && payload.get("subscription") instanceof Map) {
            Map<String, Object> subscription = (Map<String, Object>) payload.get("subscription");
            if (subscription.containsKey("id")) {
                return subscription.get("id").toString();
            }
        }
        return null;
    }
}
