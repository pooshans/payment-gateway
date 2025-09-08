package com.payment.gateway.service.impl;

import com.payment.gateway.dto.AuthorizeNetWebhookRequest;
import com.payment.gateway.dto.WebhookResponse;
import com.payment.gateway.entity.WebhookEvent;
import com.payment.gateway.repository.WebhookEventRepository;
import com.payment.gateway.service.MetricsService;
import com.payment.gateway.service.WebhookProcessor;
import com.payment.gateway.service.WebhookService;
import com.payment.gateway.util.CorrelationIdUtils;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookProcessor webhookProcessor;
    private final MetricsService metricsService;
    private final CorrelationIdUtils correlationIdUtils;

    @Value("${payment.gateway.webhook.authorizenet.signature-key:}")
    private String authorizeNetSignatureKey;

    private static final int MAX_PROCESSING_ATTEMPTS = 3;

    @PostConstruct
    public void init() {
        log.info("WebhookService initialized. Signature key configured: {}",
                authorizeNetSignatureKey != null && !authorizeNetSignatureKey.isEmpty());
    }

    @Override
    @Transactional
    public WebhookResponse processAuthorizeNetWebhook(AuthorizeNetWebhookRequest request, String rawPayload,
            String correlationId) {

        // Set correlation ID in MDC
        correlationIdUtils.setCorrelationId(correlationId);

        // Start timer for webhook processing
        Timer.Sample processingTimer = metricsService.startTimer();

        try {
            // Record webhook received metric
            metricsService.recordWebhookReceived(request.getEventType());

            log.info("Processing Authorize.Net webhook: eventType={}, notificationId={}",
                    request.getEventType(), request.getNotificationId());

            // Check if we've already processed this webhook (idempotency)
            Optional<WebhookEvent> existingEvent = webhookEventRepository.findByEventId(request.getNotificationId());
            if (existingEvent.isPresent()) {
                log.info("Duplicate webhook received: {}", request.getNotificationId());
                metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing",
                        "eventType", request.getEventType(),
                        "result", "duplicate");
                return WebhookResponse.duplicate(request.getNotificationId());
            }

            // Verify signature if key is configured
            boolean signatureValid = authorizeNetSignatureKey.isEmpty() || verifyWebhookSignature(request, rawPayload);
            if (!signatureValid) {
                log.warn("Invalid webhook signature for event: {}", request.getNotificationId());
                metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing",
                        "eventType", request.getEventType(),
                        "result", "invalid_signature");
                return WebhookResponse.invalidSignature();
            }

            // Store the webhook event
            WebhookEvent webhookEvent = WebhookEvent.builder()
                    .eventId(request.getNotificationId())
                    .eventType(request.getEventType())
                    .eventSource("AUTHORIZE_NET")
                    .payload(rawPayload)
                    .signature(request.getSignature())
                    .signatureValid(signatureValid)
                    .correlationId(correlationId)
                    .build();

            // Extract related IDs if available in the payload
            extractRelatedIds(request, webhookEvent);

            // Save the event
            webhookEvent = webhookEventRepository.save(webhookEvent);

            // Process asynchronously
            processWebhookEventAsync(webhookEvent);

            metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing",
                    "eventType", request.getEventType(),
                    "result", "accepted");

            return WebhookResponse.accepted(request.getNotificationId());
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing",
                    "eventType", request.getEventType() != null ? request.getEventType() : "unknown",
                    "result", "error");
            throw e;
        }
    }

    private void extractRelatedIds(AuthorizeNetWebhookRequest request, WebhookEvent webhookEvent) {
        try {
            if (request.getPayload() != null) {
                // Extract payment ID if available
                if (request.getPayload().containsKey("id")) {
                    webhookEvent.setRelatedPaymentId(request.getPayload().get("id").toString());
                }

                // Extract subscription ID if available
                if (request.getPayload().containsKey("subscription")
                        && request.getPayload().get("subscription") instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> subscription = (java.util.Map<String, Object>) request.getPayload()
                            .get("subscription");
                    if (subscription.containsKey("id")) {
                        webhookEvent.setRelatedSubscriptionId(subscription.get("id").toString());
                    }
                }

                // Extract customer ID if available
                if (request.getPayload().containsKey("customer")
                        && request.getPayload().get("customer") instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> customer = (java.util.Map<String, Object>) request.getPayload()
                            .get("customer");
                    if (customer.containsKey("id")) {
                        try {
                            webhookEvent.setRelatedCustomerId(Long.valueOf(customer.get("id").toString()));
                        } catch (NumberFormatException e) {
                            log.warn("Could not parse customer ID from webhook: {}", customer.get("id"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting related IDs from webhook payload", e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(AuthorizeNetWebhookRequest request, String rawPayload) {
        if (authorizeNetSignatureKey == null || authorizeNetSignatureKey.isEmpty()) {
            log.warn("Webhook signature key not configured, skipping verification");
            return true;
        }

        try {
            // Extract signature from request
            String providedSignature = request.getSignature();
            if (providedSignature == null) {
                log.warn("No signature provided in webhook");
                return false;
            }

            // Calculate expected signature using HMAC SHA-512
            Mac hmacSha512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(authorizeNetSignatureKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512");
            hmacSha512.init(secretKey);
            byte[] hash = hmacSha512.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = Base64.getEncoder().encodeToString(hash);

            // Compare signatures
            boolean isValid = calculatedSignature.equals(providedSignature);
            if (!isValid) {
                log.warn("Webhook signature mismatch");
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    @Override
    @Async("webhookTaskExecutor")
    public void processWebhookEventAsync(WebhookEvent webhookEvent) {
        // Set the correlation ID in the async thread
        correlationIdUtils.setCorrelationId(webhookEvent.getCorrelationId());

        Timer.Sample processingTimer = metricsService.startTimer();
        String eventType = webhookEvent.getEventType();

        log.info("Processing webhook event asynchronously: {}, type: {}", webhookEvent.getEventId(), eventType);

        try {
            boolean processed = webhookProcessor.processWebhookEvent(webhookEvent);

            // Update event status
            webhookEvent.setProcessed(processed);
            webhookEvent.setProcessingAttempts(webhookEvent.getProcessingAttempts() + 1);
            webhookEvent.setProcessedAt(LocalDateTime.now());

            if (processed) {
                metricsService.recordWebhookProcessedSuccess(eventType);
                metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing.async",
                        "eventType", eventType, "result", "success",
                        "attempt", String.valueOf(webhookEvent.getProcessingAttempts()));
            } else {
                log.warn("Failed to process webhook event: {}", webhookEvent.getEventId());
                webhookEvent.setLastError("Processing failed");
                metricsService.recordWebhookProcessedFailure(eventType);
                metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing.async",
                        "eventType", eventType, "result", "failure",
                        "attempt", String.valueOf(webhookEvent.getProcessingAttempts()));
            }
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", webhookEvent.getEventId(), e);
            webhookEvent.setProcessed(false);
            webhookEvent.setProcessingAttempts(webhookEvent.getProcessingAttempts() + 1);
            webhookEvent.setLastError(e.getMessage());

            metricsService.recordWebhookProcessedFailure(eventType);
            metricsService.stopTimer(processingTimer, "payment.gateway.webhook.processing.async",
                    "eventType", eventType, "result", "error",
                    "attempt", String.valueOf(webhookEvent.getProcessingAttempts()));
        } finally {
            // Save updated event
            webhookEventRepository.save(webhookEvent);

            // Clear the correlation ID from the MDC
            correlationIdUtils.clearCorrelationId();
        }
    }

    @Override
    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void retryFailedEvents() {
        Timer.Sample retryTimer = metricsService.startTimer();

        log.info("Retrying failed webhook events...");

        try {
            List<WebhookEvent> failedEvents = webhookEventRepository.findByProcessedFalseOrderByReceivedAtAsc();

            log.info("Found {} failed webhook events to retry", failedEvents.size());

            int maxAttemptsReached = 0;
            int eventsToProcess = 0;

            for (WebhookEvent event : failedEvents) {
                // Skip events that have reached max retry attempts
                if (event.getProcessingAttempts() >= MAX_PROCESSING_ATTEMPTS) {
                    log.warn("Webhook event {} has reached maximum retry attempts, skipping", event.getEventId());
                    maxAttemptsReached++;
                    continue;
                }

                // Process event
                log.info("Retrying webhook event: {} (attempt {}/{})",
                        event.getEventId(), event.getProcessingAttempts() + 1, MAX_PROCESSING_ATTEMPTS);
                processWebhookEventAsync(event);
                eventsToProcess++;
            }

            metricsService.stopTimer(retryTimer, "payment.gateway.webhook.retry",
                    "total", String.valueOf(failedEvents.size()),
                    "processed", String.valueOf(eventsToProcess),
                    "maxAttemptsReached", String.valueOf(maxAttemptsReached));
        } catch (Exception e) {
            log.error("Error retrying failed webhook events", e);
            metricsService.stopTimer(retryTimer, "payment.gateway.webhook.retry", "result", "error");
        }
    }
}
