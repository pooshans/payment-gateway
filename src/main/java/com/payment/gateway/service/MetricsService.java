package com.payment.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing application metrics and observability.
 * 
 * This service centralizes the creation and tracking of various metrics
 * throughout
 * the payment gateway application. It uses Spring Boot's Micrometer framework
 * to
 * record and expose metrics that can be consumed by monitoring systems like
 * Prometheus and Grafana.
 * 
 * Key metrics tracked include:
 * - Payment transaction counts (success/failure)
 * - Payment amounts by type and status
 * - API response times
 * - Webhook processing metrics
 * - Subscription lifecycle events
 * - System health indicators
 * 
 * The service provides both counters for tracking discrete events and timers
 * for measuring latency of operations.
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Counter webhookReceivedCounter;
    private final Counter webhookProcessedSuccessCounter;
    private final Counter webhookProcessedFailureCounter;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.paymentSuccessCounter = Counter.builder("payment.gateway.transactions")
                .tag("result", "success")
                .description("Number of successful payment transactions")
                .register(meterRegistry);

        this.paymentFailureCounter = Counter.builder("payment.gateway.transactions")
                .tag("result", "failure")
                .description("Number of failed payment transactions")
                .register(meterRegistry);

        this.webhookReceivedCounter = Counter.builder("payment.gateway.webhook.events")
                .tag("action", "received")
                .description("Number of webhook events received")
                .register(meterRegistry);

        this.webhookProcessedSuccessCounter = Counter.builder("payment.gateway.webhook.events")
                .tag("action", "processed")
                .tag("result", "success")
                .description("Number of webhook events processed successfully")
                .register(meterRegistry);

        this.webhookProcessedFailureCounter = Counter.builder("payment.gateway.webhook.events")
                .tag("action", "processed")
                .tag("result", "failure")
                .description("Number of webhook events that failed processing")
                .register(meterRegistry);
    }

    /**
     * Record a successful payment transaction
     */
    public void recordSuccessfulPayment() {
        paymentSuccessCounter.increment();
    }

    /**
     * Record a failed payment transaction
     */
    public void recordFailedPayment() {
        paymentFailureCounter.increment();
    }

    /**
     * Record a received webhook event
     * 
     * @param eventType the type of webhook event
     */
    public void recordWebhookReceived(String eventType) {
        webhookReceivedCounter.increment();
        meterRegistry.counter("payment.gateway.webhook.events.by.type", "type", eventType, "action", "received")
                .increment();
    }

    /**
     * Record a successfully processed webhook event
     * 
     * @param eventType the type of webhook event
     */
    public void recordWebhookProcessedSuccess(String eventType) {
        webhookProcessedSuccessCounter.increment();
        meterRegistry.counter("payment.gateway.webhook.events.by.type", "type", eventType, "action", "processed",
                "result", "success").increment();
    }

    /**
     * Record a failed webhook event processing
     * 
     * @param eventType the type of webhook event
     */
    public void recordWebhookProcessedFailure(String eventType) {
        webhookProcessedFailureCounter.increment();
        meterRegistry.counter("payment.gateway.webhook.events.by.type", "type", eventType, "action", "processed",
                "result", "failure").increment();
    }

    /**
     * Record the duration of an operation with the given name
     * 
     * @param timerName the name of the timer/operation
     * @param duration  the duration in milliseconds
     * @param tags      additional tags as key-value pairs (must be even number of
     *                  arguments)
     */
    public void recordOperationDuration(String timerName, long duration, String... tags) {
        Timer timer = meterRegistry.timer(timerName, tags);
        timer.record(duration, TimeUnit.MILLISECONDS);

        // Log slow operations (> 1 second)
        if (duration > 1000) {
            log.warn("Slow operation detected: {} took {}ms", timerName, duration);
        }
    }

    /**
     * Create and start a timer for measuring operation duration
     * 
     * @param operationName the name of the operation
     * @return a Timer.Sample instance
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop a timer and record the duration
     * 
     * @param sample    the Timer.Sample instance to stop
     * @param timerName the name of the timer
     * @param tags      additional tags as key-value pairs
     */
    public void stopTimer(Timer.Sample sample, String timerName, String... tags) {
        long durationNanos = sample.stop(meterRegistry.timer(timerName, tags));
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        // Log slow operations (> 1 second)
        if (durationMillis > 1000) {
            log.warn("Slow operation detected: {} took {}ms", timerName, durationMillis);
        }
    }
}
