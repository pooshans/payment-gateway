package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.AuthorizeNetWebhookRequest;
import com.payment.gateway.dto.WebhookResponse;
import com.payment.gateway.service.MetricsService;
import com.payment.gateway.service.WebhookService;
import com.payment.gateway.util.CorrelationIdUtils;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST controller for handling Authorize.NET webhook notifications.
 * 
 * This controller provides endpoints that receive asynchronous event
 * notifications from
 * the Authorize.NET payment gateway, including payment events, refund events,
 * and subscription events.
 * 
 * The controller implements idempotent processing to safely handle duplicate
 * webhook deliveries,
 * validates webhook signatures for authenticity, and processes events
 * asynchronously to ensure
 * quick response times.
 * 
 * All webhook events are tracked with metrics and distributed tracing for
 * observability.
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final CorrelationIdUtils correlationIdUtils;

    /**
     * Endpoint for receiving Authorize.Net webhook notifications
     */
    @PostMapping("/authorizenet/notification")
    public ResponseEntity<WebhookResponse> handleAuthorizeNetWebhook(@RequestBody String rawPayload) {
        Timer.Sample timerSample = metricsService.startTimer();
        String correlationId = correlationIdUtils.getCurrentCorrelationId();

        log.info("Received Authorize.Net webhook notification");

        try {
            // Parse the request body
            AuthorizeNetWebhookRequest request = objectMapper.readValue(rawPayload, AuthorizeNetWebhookRequest.class);

            // Record webhook received metric
            metricsService.recordWebhookReceived(request.getEventType());

            // Process the webhook
            WebhookResponse response = webhookService.processAuthorizeNetWebhook(request, rawPayload, correlationId);

            // Record metrics based on the result
            if ("success".equals(response.getStatus())) {
                metricsService.recordWebhookProcessedSuccess(request.getEventType());
                metricsService.stopTimer(timerSample, "payment.gateway.webhook.processing",
                        "eventType", request.getEventType(),
                        "result", "success");
                return ResponseEntity.ok(response);
            } else {
                metricsService.recordWebhookProcessedFailure(request.getEventType());
                metricsService.stopTimer(timerSample, "payment.gateway.webhook.processing",
                        "eventType", request.getEventType(),
                        "result", "error");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (IOException e) {
            log.error("Error parsing webhook payload: {}", e.getMessage(), e);
            metricsService.stopTimer(timerSample, "payment.gateway.webhook.processing",
                    "eventType", "unknown",
                    "result", "parse_error");
            return ResponseEntity.badRequest().body(WebhookResponse.error("Invalid request payload"));
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            metricsService.stopTimer(timerSample, "payment.gateway.webhook.processing",
                    "eventType", "unknown",
                    "result", "system_error");
            return ResponseEntity.internalServerError().body(WebhookResponse.error("Internal server error"));
        }
    }

    /**
     * Health check endpoint for Authorize.Net to test the webhook integration
     */
    @GetMapping("/authorizenet/health")
    public ResponseEntity<String> healthCheck() {
        log.debug("Webhook health check requested");
        return ResponseEntity.ok("Webhook endpoint is healthy");
    }
}
