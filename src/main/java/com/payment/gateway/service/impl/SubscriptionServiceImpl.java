package com.payment.gateway.service.impl;

import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.PaymentMethod;
import com.payment.gateway.entity.Subscription;
import com.payment.gateway.exception.PaymentProcessingException;
import com.payment.gateway.exception.ResourceNotFoundException;
import com.payment.gateway.repository.CustomerRepository;
import com.payment.gateway.repository.PaymentMethodRepository;
import com.payment.gateway.repository.PaymentRepository;
import com.payment.gateway.repository.SubscriptionRepository;
import com.payment.gateway.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the SubscriptionService for managing recurring payments and
 * subscriptions.
 * Handles subscription lifecycle operations including creation, updates,
 * cancellations,
 * and scheduled billing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentRepository paymentRepository;

    // Constants for subscription status
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * Creates a new subscription for a customer and processes the initial payment.
     * 
     * @param customerId       Customer identifier
     * @param recurringRequest Request containing subscription and payment details
     * @param correlationId    Correlation ID for request tracing
     * @param idempotencyKey   Key to prevent duplicate processing
     * @return Payment response with details of the initial payment
     */
    @Override
    @Transactional
    public PaymentResponse createSubscription(Long customerId, RecurringPaymentRequest recurringRequest,
            String correlationId, String idempotencyKey) {
        log.info("Creating subscription for customer ID: {} with correlation ID: {}", customerId, correlationId);

        // Validate customer exists
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        // Validate payment card is not expired
        validateCardExpiration(recurringRequest.getExpirationDate());

        // Create or retrieve payment method
        PaymentMethod paymentMethod = createOrRetrievePaymentMethod(customer, recurringRequest);
        paymentMethodRepository.save(paymentMethod);

        // Calculate billing dates
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime nextBillingDate = calculateNextBillingDate(startDate,
                recurringRequest.getIntervalLength(),
                recurringRequest.getIntervalUnit());

        // Create subscription entity
        Subscription subscription = Subscription.builder()
                .subscriptionId(generateSubscriptionId())
                .customer(customer)
                .paymentMethod(paymentMethod)
                .status(STATUS_ACTIVE)
                .amount(recurringRequest.getAmount())
                .currencyCode(recurringRequest.getCurrencyCode() != null
                        ? recurringRequest.getCurrencyCode()
                        : "USD")
                .name(recurringRequest.getSubscriptionName())
                .description("Subscription for " + recurringRequest.getSubscriptionName())
                .intervalLength(recurringRequest.getIntervalLength())
                .intervalUnit(recurringRequest.getIntervalUnit().toUpperCase())
                .totalCycles(recurringRequest.getTotalOccurrences())
                .completedCycles(0)
                .startDate(startDate)
                .nextBillingDate(nextBillingDate)
                .build();

        // Save subscription
        subscriptionRepository.save(subscription);

        try {
            // Process initial payment
            PaymentResponse initialPaymentResponse = processInitialSubscriptionPayment(
                    subscription, recurringRequest, correlationId, idempotencyKey);

            log.info("Subscription created successfully with ID: {}", subscription.getSubscriptionId());
            return initialPaymentResponse;
        } catch (Exception e) {
            // Handle payment failure
            subscription.setStatus(STATUS_FAILED);
            subscriptionRepository.save(subscription);
            log.error("Failed to process initial payment for subscription {}: {}",
                    subscription.getSubscriptionId(), e.getMessage(), e);
            throw new PaymentProcessingException("Failed to process initial payment: " + e.getMessage());
        }
    }

    /**
     * Retrieves a subscription by its ID.
     * 
     * @param subscriptionId Unique subscription identifier
     * @return Optional containing the subscription if found
     */
    @Override
    public Optional<Subscription> getSubscriptionById(String subscriptionId) {
        return subscriptionRepository.findBySubscriptionId(subscriptionId);
    }

    /**
     * Gets all subscriptions for a specific customer.
     * 
     * @param customer Customer entity
     * @return List of subscriptions belonging to the customer
     */
    @Override
    public List<Subscription> getSubscriptionsByCustomer(Customer customer) {
        return subscriptionRepository.findByCustomer(customer);
    }

    /**
     * Gets only active subscriptions for a customer.
     * 
     * @param customer Customer entity
     * @return List of active subscriptions for the customer
     */
    @Override
    public List<Subscription> getActiveSubscriptionsByCustomer(Customer customer) {
        return subscriptionRepository.findByCustomerAndStatus(customer, STATUS_ACTIVE);
    }

    /**
     * Cancels an active subscription.
     * 
     * @param subscriptionId Subscription to cancel
     * @param reason         Cancellation reason
     * @param correlationId  Correlation ID for request tracing
     * @return Payment response with cancellation details
     */
    @Override
    @Transactional
    public PaymentResponse cancelSubscription(String subscriptionId, String reason, String correlationId) {
        log.info("Cancelling subscription with ID: {} and correlation ID: {}", subscriptionId, correlationId);

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        // Validate cancellation is possible
        if (STATUS_CANCELLED.equals(subscription.getStatus())) {
            throw new PaymentProcessingException("Subscription is already cancelled");
        } else if (STATUS_EXPIRED.equals(subscription.getStatus())) {
            throw new PaymentProcessingException("Cannot cancel an expired subscription");
        }

        // Update subscription status
        subscription.setStatus(STATUS_CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setCancelReason(reason != null ? reason : "Customer requested cancellation");
        subscription.setEndDate(LocalDateTime.now());

        subscriptionRepository.save(subscription);

        log.info("Subscription cancelled successfully: {}", subscriptionId);

        // Create response
        PaymentResponse response = PaymentResponse.builder()
                .status(STATUS_CANCELLED)
                .transactionId(subscriptionId)
                .responseMessage("Subscription cancelled successfully")
                .correlationId(correlationId)
                .build();

        return response;
    }

    /**
     * Updates the billing amount for an active subscription.
     * 
     * @param subscriptionId Subscription to update
     * @param newAmount      New billing amount
     * @param correlationId  Correlation ID for request tracing
     * @return Payment response with update details
     */
    @Override
    @Transactional
    public PaymentResponse updateSubscriptionAmount(String subscriptionId, BigDecimal newAmount, String correlationId) {
        log.info("Updating amount for subscription ID: {} to {} with correlation ID: {}",
                subscriptionId, newAmount, correlationId);

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        // Validate update is possible
        if (!STATUS_ACTIVE.equals(subscription.getStatus())) {
            throw new PaymentProcessingException("Can only update active subscriptions");
        }

        // Validate amount
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Amount must be greater than zero");
        }

        BigDecimal oldAmount = subscription.getAmount();
        subscription.setAmount(newAmount);
        subscriptionRepository.save(subscription);

        log.info("Subscription amount updated successfully from {} to {}: {}",
                oldAmount, newAmount, subscriptionId);

        // Create response
        PaymentResponse response = PaymentResponse.builder()
                .status("UPDATED")
                .transactionId(subscriptionId)
                .amount(newAmount)
                .currencyCode(subscription.getCurrencyCode())
                .responseMessage("Subscription amount updated successfully")
                .correlationId(correlationId)
                .build();

        return response;
    }

    /**
     * Processes all due subscription payments based on their billing cycles.
     * This method should be scheduled to run daily.
     * 
     * @return Number of successfully processed payments
     */
    @Override
    @Transactional
    public int processScheduledPayments() {
        log.info("Processing scheduled subscription payments");
        LocalDateTime now = LocalDateTime.now();

        // Find subscriptions with due payments (active and billing date in the past)
        List<Subscription> dueSubscriptions = subscriptionRepository.findAll().stream()
                .filter(sub -> STATUS_ACTIVE.equals(sub.getStatus()))
                .filter(sub -> sub.getNextBillingDate() != null && sub.getNextBillingDate().isBefore(now))
                .collect(Collectors.toList());

        log.info("Found {} subscriptions due for payment", dueSubscriptions.size());

        int successCount = 0;

        for (Subscription subscription : dueSubscriptions) {
            try {
                // Check if payment method is expired
                if (isPaymentMethodExpired(subscription.getPaymentMethod())) {
                    handleExpiredPaymentMethod(subscription);
                    continue;
                }

                // Process payment
                processRecurringPayment(subscription);

                // Update subscription
                subscription.setCompletedCycles(subscription.getCompletedCycles() + 1);

                // Calculate next billing date
                LocalDateTime nextBillingDate = calculateNextBillingDate(
                        subscription.getNextBillingDate(),
                        subscription.getIntervalLength(),
                        subscription.getIntervalUnit());
                subscription.setNextBillingDate(nextBillingDate);

                // Check if subscription is completed (reached total cycles)
                if (subscription.getTotalCycles() != null &&
                        subscription.getCompletedCycles() >= subscription.getTotalCycles()) {
                    subscription.setStatus(STATUS_EXPIRED);
                    subscription.setEndDate(LocalDateTime.now());
                    log.info("Subscription {} completed all cycles and is now expired",
                            subscription.getSubscriptionId());
                }

                subscriptionRepository.save(subscription);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to process payment for subscription {}: {}",
                        subscription.getSubscriptionId(), e.getMessage(), e);

                // Mark subscription as failed after repeated failures
                // In a production system, we would implement a retry mechanism with failure
                // threshold
                markSubscriptionPaymentFailed(subscription);
            }
        }

        log.info("Successfully processed {} subscription payments", successCount);
        return successCount;
    }

    /**
     * Updates the next billing date for a subscription.
     * 
     * @param subscriptionId  Subscription to update
     * @param nextBillingDate New billing date
     * @return Updated subscription
     */
    @Override
    @Transactional
    public Subscription updateNextBillingDate(String subscriptionId, LocalDateTime nextBillingDate) {
        if (nextBillingDate == null) {
            throw new IllegalArgumentException("Next billing date cannot be null");
        }

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        // Only allow future dates
        if (nextBillingDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Next billing date must be in the future");
        }

        subscription.setNextBillingDate(nextBillingDate);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Temporarily suspends an active subscription.
     * 
     * @param subscriptionId Subscription to suspend
     * @param reason         Suspension reason
     * @param correlationId  Correlation ID for request tracing
     * @return Payment response with suspension details
     */
    @Override
    @Transactional
    public PaymentResponse suspendSubscription(String subscriptionId, String reason, String correlationId) {
        log.info("Suspending subscription with ID: {} and correlation ID: {}", subscriptionId, correlationId);

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        if (!STATUS_ACTIVE.equals(subscription.getStatus())) {
            throw new PaymentProcessingException("Only active subscriptions can be suspended");
        }

        // Update subscription status
        subscription.setStatus(STATUS_SUSPENDED);
        subscriptionRepository.save(subscription);

        log.info("Subscription suspended successfully: {}", subscriptionId);

        // Create response
        PaymentResponse response = PaymentResponse.builder()
                .status(STATUS_SUSPENDED)
                .transactionId(subscriptionId)
                .responseMessage("Subscription suspended successfully")
                .correlationId(correlationId)
                .build();

        return response;
    }

    /**
     * Reactivates a suspended subscription.
     * 
     * @param subscriptionId Subscription to reactivate
     * @param correlationId  Correlation ID for request tracing
     * @return Payment response with reactivation details
     */
    @Override
    @Transactional
    public PaymentResponse reactivateSubscription(String subscriptionId, String correlationId) {
        log.info("Reactivating subscription with ID: {} and correlation ID: {}", subscriptionId, correlationId);

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        if (!STATUS_SUSPENDED.equals(subscription.getStatus())) {
            throw new PaymentProcessingException("Only suspended subscriptions can be reactivated");
        }

        // Check if payment method is valid before reactivation
        if (isPaymentMethodExpired(subscription.getPaymentMethod())) {
            throw new PaymentProcessingException("Cannot reactivate subscription with expired payment method");
        }

        // Update subscription status
        subscription.setStatus(STATUS_ACTIVE);

        // Recalculate next billing date if it's in the past
        LocalDateTime now = LocalDateTime.now();
        if (subscription.getNextBillingDate() == null || subscription.getNextBillingDate().isBefore(now)) {
            LocalDateTime nextBillingDate = calculateNextBillingDate(
                    now,
                    subscription.getIntervalLength(),
                    subscription.getIntervalUnit());
            subscription.setNextBillingDate(nextBillingDate);
        }

        subscriptionRepository.save(subscription);

        log.info("Subscription reactivated successfully: {}", subscriptionId);

        // Create response
        PaymentResponse response = PaymentResponse.builder()
                .status(STATUS_ACTIVE)
                .transactionId(subscriptionId)
                .responseMessage("Subscription reactivated successfully")
                .correlationId(correlationId)
                .build();

        return response;
    }

    // Helper methods

    /**
     * Creates or retrieves a payment method for the subscription.
     * 
     * @param customer         Customer entity
     * @param recurringRequest Request containing payment details
     * @return New or existing PaymentMethod entity
     */
    private PaymentMethod createOrRetrievePaymentMethod(Customer customer, RecurringPaymentRequest recurringRequest) {
        // Check if a payment method with the same card already exists
        String last4Digits = recurringRequest.getCardNumber().substring(
                recurringRequest.getCardNumber().length() - 4);

        Optional<PaymentMethod> existingMethod = paymentMethodRepository.findByCustomer(customer).stream()
                .filter(pm -> pm.getLast4Digits().equals(last4Digits))
                .filter(pm -> {
                    String expMonth = recurringRequest.getExpirationDate().substring(0, 2);
                    String expYear = recurringRequest.getExpirationDate().substring(3);
                    return pm.getExpirationMonth().equals(expMonth) &&
                            pm.getExpirationYear().equals(expYear);
                })
                .findFirst();

        if (existingMethod.isPresent()) {
            return existingMethod.get();
        }

        // In a real implementation, we would tokenize the card data with the payment
        // provider
        // and store the token rather than actual card data
        return PaymentMethod.builder()
                .customer(customer)
                .type("CREDIT_CARD")
                .last4Digits(last4Digits)
                .expirationMonth(recurringRequest.getExpirationDate().substring(0, 2))
                .expirationYear(recurringRequest.getExpirationDate().substring(3))
                .cardholderName(recurringRequest.getCardholderName())
                .isDefault(true)
                .build();
    }

    /**
     * Calculates the next billing date based on interval.
     * 
     * @param fromDate       Starting date
     * @param intervalLength Number of time units
     * @param intervalUnit   Type of time unit (DAY, WEEK, MONTH, YEAR)
     * @return Next billing date
     */
    private LocalDateTime calculateNextBillingDate(LocalDateTime fromDate, Integer intervalLength,
            String intervalUnit) {
        return switch (intervalUnit.toUpperCase()) {
            case "DAY" -> fromDate.plusDays(intervalLength);
            case "WEEK" -> fromDate.plusWeeks(intervalLength);
            case "MONTH" -> fromDate.plusMonths(intervalLength);
            case "YEAR" -> fromDate.plusYears(intervalLength);
            default -> throw new IllegalArgumentException("Invalid interval unit: " + intervalUnit);
        };
    }

    /**
     * Generates a unique subscription ID.
     * 
     * @return Unique subscription identifier
     */
    private String generateSubscriptionId() {
        return "SUB-" + UUID.randomUUID().toString();
    }

    /**
     * Validates that the provided card is not expired.
     * 
     * @param expirationDate Card expiration date in MM/YYYY format
     * @throws PaymentProcessingException if card is expired
     */
    private void validateCardExpiration(String expirationDate) {
        // Parse expiration date (MM/YYYY)
        int month = Integer.parseInt(expirationDate.substring(0, 2));
        int year = Integer.parseInt(expirationDate.substring(3));

        // Get current date
        YearMonth currentYearMonth = YearMonth.now();
        YearMonth expirationYearMonth = YearMonth.of(year, month);

        if (expirationYearMonth.isBefore(currentYearMonth)) {
            throw new PaymentProcessingException("Card has expired. Please update payment method.");
        }
    }

    /**
     * Checks if a payment method is expired.
     * 
     * @param paymentMethod Payment method to check
     * @return true if expired, false otherwise
     */
    private boolean isPaymentMethodExpired(PaymentMethod paymentMethod) {
        if (paymentMethod == null || paymentMethod.getExpirationMonth() == null
                || paymentMethod.getExpirationYear() == null) {
            return true;
        }

        int month = Integer.parseInt(paymentMethod.getExpirationMonth());
        int year = Integer.parseInt(paymentMethod.getExpirationYear());

        YearMonth currentYearMonth = YearMonth.now();
        YearMonth expirationYearMonth = YearMonth.of(year, month);

        return expirationYearMonth.isBefore(currentYearMonth);
    }

    /**
     * Handles expired payment methods for subscriptions.
     * 
     * @param subscription Subscription with expired payment method
     */
    private void handleExpiredPaymentMethod(Subscription subscription) {
        log.warn("Payment method expired for subscription {}", subscription.getSubscriptionId());
        subscription.setStatus(STATUS_SUSPENDED);
        subscription.setCancelReason("Payment method expired");
        subscriptionRepository.save(subscription);

        // In a real system, we would trigger a notification to the customer here
    }

    /**
     * Marks a subscription as having failed payment after multiple attempts.
     * 
     * @param subscription Subscription with failed payment
     */
    private void markSubscriptionPaymentFailed(Subscription subscription) {
        // In a production system, we might implement a more sophisticated retry
        // mechanism
        // with a failure threshold before suspending
        subscription.setStatus(STATUS_SUSPENDED);
        subscription.setCancelReason("Payment processing failed");
        subscriptionRepository.save(subscription);

        log.warn("Subscription {} suspended due to payment failure", subscription.getSubscriptionId());

        // In a real system, we would trigger a notification to the customer here
    }

    /**
     * Processes the initial payment for a new subscription.
     * 
     * @param subscription     Subscription entity
     * @param recurringRequest Request containing payment details
     * @param correlationId    Correlation ID for request tracing
     * @param idempotencyKey   Key to prevent duplicate processing
     * @return Payment response with initial payment details
     */
    private PaymentResponse processInitialSubscriptionPayment(
            Subscription subscription,
            RecurringPaymentRequest recurringRequest,
            String correlationId,
            String idempotencyKey) {

        // In a real implementation, this would call the payment gateway to process the
        // initial payment
        // For now, we'll simulate a successful payment

        LocalDateTime transactionDate = LocalDateTime.now();

        Payment payment = Payment.builder()
                .paymentId("PAY-" + UUID.randomUUID().toString())
                .customer(subscription.getCustomer())
                .amount(subscription.getAmount())
                .currencyCode(subscription.getCurrencyCode())
                .status("CAPTURED")
                .paymentGateway("AUTHORIZE_NET")
                .description("Initial payment for subscription: " + subscription.getName())
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .last4Digits(recurringRequest.getCardNumber().substring(recurringRequest.getCardNumber().length() - 4))
                .subscriptionId(subscription.getSubscriptionId())
                .build();

        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .status("CAPTURED")
                .transactionId(payment.getPaymentId())
                .responseMessage("Initial subscription payment processed successfully")
                .correlationId(correlationId)
                .amount(subscription.getAmount())
                .currencyCode(subscription.getCurrencyCode())
                .last4Digits(payment.getLast4Digits())
                .transactionDate(transactionDate)
                .build();
    }

    /**
     * Processes a recurring payment for an existing subscription.
     * 
     * @param subscription Subscription to bill
     */
    private void processRecurringPayment(Subscription subscription) {
        // In a real implementation, this would call the payment gateway to process the
        // recurring payment
        // based on the stored payment method

        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .paymentId("PAY-" + UUID.randomUUID().toString())
                .customer(subscription.getCustomer())
                .amount(subscription.getAmount())
                .currencyCode(subscription.getCurrencyCode())
                .status("CAPTURED")
                .paymentGateway("AUTHORIZE_NET")
                .description("Recurring payment for subscription: " + subscription.getName())
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .last4Digits(subscription.getPaymentMethod().getLast4Digits())
                .subscriptionId(subscription.getSubscriptionId())
                .build();

        paymentRepository.save(payment);

        log.info("Processed recurring payment {} for subscription {}",
                payment.getPaymentId(), subscription.getSubscriptionId());
    }
}
