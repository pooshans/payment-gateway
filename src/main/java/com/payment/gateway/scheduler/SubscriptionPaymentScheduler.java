package com.payment.gateway.scheduler;

import com.payment.gateway.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentScheduler {

    private final SubscriptionService subscriptionService;

    /**
     * Schedule to process recurring payments for active subscriptions.
     * Runs every day at midnight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void processRecurringPayments() {
        log.info("Starting scheduled processing of recurring subscription payments");

        try {
            int processedCount = subscriptionService.processScheduledPayments();
            log.info("Successfully processed {} subscription payments", processedCount);
        } catch (Exception e) {
            log.error("Error processing scheduled subscription payments: {}", e.getMessage(), e);
        }
    }
}
