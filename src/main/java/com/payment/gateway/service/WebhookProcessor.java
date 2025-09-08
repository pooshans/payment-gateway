package com.payment.gateway.service;

import com.payment.gateway.entity.WebhookEvent;

/**
 * Service for processing different types of webhook events
 */
public interface WebhookProcessor {

    /**
     * Process a webhook event based on its type
     * 
     * @param webhookEvent The event to process
     * @return true if processed successfully, false otherwise
     */
    boolean processWebhookEvent(WebhookEvent webhookEvent);

    /**
     * Process a payment authorization capture event
     * 
     * @param webhookEvent The webhook event
     * @return true if processed successfully, false otherwise
     */
    boolean processPaymentAuthCaptureEvent(WebhookEvent webhookEvent);

    /**
     * Process a refund event
     * 
     * @param webhookEvent The webhook event
     * @return true if processed successfully, false otherwise
     */
    boolean processRefundEvent(WebhookEvent webhookEvent);

    /**
     * Process a subscription event
     * 
     * @param webhookEvent The webhook event
     * @return true if processed successfully, false otherwise
     */
    boolean processSubscriptionEvent(WebhookEvent webhookEvent);
}
