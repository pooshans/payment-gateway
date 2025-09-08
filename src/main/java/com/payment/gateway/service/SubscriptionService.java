package com.payment.gateway.service;

import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing recurring billing subscriptions.
 * 
 * This service handles the complete lifecycle of subscriptions including
 * creation,
 * retrieval, updating, and cancellation. It integrates with Authorize.NET's ARB
 * (Automated Recurring Billing) system to manage subscription processing.
 * 
 * Key features include:
 * - Creating subscriptions with various billing cycles (monthly, quarterly,
 * etc.)
 * - Supporting trial periods with different pricing
 * - Managing customer billing information
 * - Scheduling and tracking recurring payments
 * - Providing subscription status and history
 * 
 * All operations include correlation IDs for request tracking, and subscription
 * creation supports idempotency keys to prevent duplicate processing.
 */
public interface SubscriptionService {

    /**
     * Create a new subscription for a customer.
     *
     * @param customerId              The ID of the customer
     * @param recurringPaymentRequest The request containing subscription details
     * @param correlationId           The correlation ID for request tracking
     * @param idempotencyKey          The idempotency key to prevent duplicate
     *                                processing
     * @return The payment response containing subscription information
     */
    PaymentResponse createSubscription(Long customerId, RecurringPaymentRequest recurringPaymentRequest,
            String correlationId, String idempotencyKey);

    /**
     * Get a subscription by its ID.
     *
     * @param subscriptionId The subscription ID
     * @return Optional containing the subscription if found
     */
    Optional<Subscription> getSubscriptionById(String subscriptionId);

    /**
     * Get all subscriptions for a customer.
     *
     * @param customer The customer entity
     * @return List of subscriptions for the customer
     */
    List<Subscription> getSubscriptionsByCustomer(Customer customer);

    /**
     * Get active subscriptions for a customer.
     *
     * @param customer The customer entity
     * @return List of active subscriptions for the customer
     */
    List<Subscription> getActiveSubscriptionsByCustomer(Customer customer);

    /**
     * Cancel a subscription.
     *
     * @param subscriptionId The subscription ID
     * @param reason         The reason for cancellation
     * @param correlationId  The correlation ID for request tracking
     * @return PaymentResponse containing the cancellation status
     */
    PaymentResponse cancelSubscription(String subscriptionId, String reason, String correlationId);

    /**
     * Update a subscription amount.
     *
     * @param subscriptionId The subscription ID
     * @param newAmount      The new amount for the subscription
     * @param correlationId  The correlation ID for request tracking
     * @return PaymentResponse containing the update status
     */
    PaymentResponse updateSubscriptionAmount(String subscriptionId, BigDecimal newAmount, String correlationId);

    /**
     * Process upcoming subscription payments that are due.
     * This method should be called by a scheduled job.
     *
     * @return Number of successfully processed subscription payments
     */
    int processScheduledPayments();

    /**
     * Update the next billing date for a subscription.
     *
     * @param subscriptionId  The subscription ID
     * @param nextBillingDate The new next billing date
     * @return Updated subscription
     */
    Subscription updateNextBillingDate(String subscriptionId, LocalDateTime nextBillingDate);

    /**
     * Suspend a subscription temporarily.
     *
     * @param subscriptionId The subscription ID
     * @param reason         The reason for suspension
     * @param correlationId  The correlation ID for request tracking
     * @return PaymentResponse containing the suspension status
     */
    PaymentResponse suspendSubscription(String subscriptionId, String reason, String correlationId);

    /**
     * Reactivate a suspended subscription.
     *
     * @param subscriptionId The subscription ID
     * @param correlationId  The correlation ID for request tracking
     * @return PaymentResponse containing the reactivation status
     */
    PaymentResponse reactivateSubscription(String subscriptionId, String correlationId);
}
