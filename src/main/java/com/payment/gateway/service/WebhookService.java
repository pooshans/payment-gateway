package com.payment.gateway.service;

import com.payment.gateway.dto.AuthorizeNetWebhookRequest;
import com.payment.gateway.dto.WebhookResponse;
import com.payment.gateway.entity.WebhookEvent;

/**
 * Service interface for handling Authorize.NET webhook notifications.
 * 
 * This service is responsible for receiving, validating, and processing webhook
 * events
 * from Authorize.NET. It ensures the authenticity of webhooks through signature
 * validation,
 * provides idempotent processing to handle duplicate events, and processes
 * events asynchronously
 * for optimal performance.
 * 
 * The service handles various event types including payment notifications,
 * refund confirmations,
 * and subscription events, updating the application's state accordingly.
 */
public interface WebhookService {

    /**
     * Process an incoming webhook from Authorize.Net
     * 
     * @param request       The webhook request
     * @param rawPayload    The raw JSON payload as string
     * @param correlationId Correlation ID for tracking
     * @return A response indicating success or failure
     */
    WebhookResponse processAuthorizeNetWebhook(AuthorizeNetWebhookRequest request, String rawPayload,
            String correlationId);

    /**
     * Verify the signature of an incoming webhook
     * 
     * @param request    The webhook request
     * @param rawPayload The raw JSON payload
     * @return true if valid, false otherwise
     */
    boolean verifyWebhookSignature(AuthorizeNetWebhookRequest request, String rawPayload);

    /**
     * Process a stored webhook event asynchronously
     * 
     * @param webhookEvent The stored webhook event
     */
    void processWebhookEventAsync(WebhookEvent webhookEvent);

    /**
     * Retry processing of failed webhook events
     */
    void retryFailedEvents();
}
