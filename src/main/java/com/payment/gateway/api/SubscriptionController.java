package com.payment.gateway.api;

import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.dto.SubscriptionUpdateRequest;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Subscription;
import com.payment.gateway.exception.ResourceNotFoundException;
import com.payment.gateway.repository.CustomerRepository;
import com.payment.gateway.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing recurring billing subscriptions.
 * 
 * This controller provides endpoints for creating new subscriptions, retrieving
 * subscription details,
 * updating subscription properties, canceling subscriptions, and listing all
 * subscriptions for a customer.
 * 
 * It integrates with the Authorize.NET ARB (Automated Recurring Billing) system
 * to manage subscription
 * lifecycle. All operations include correlation IDs for request tracing and
 * follow REST principles.
 * 
 * The controller uses Lombok annotations to reduce boilerplate code and
 * delegates subscription
 * processing to the SubscriptionService.
 */
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CustomerRepository customerRepository;

    @PostMapping("/customer/{customerId}")
    public ResponseEntity<PaymentResponse> createSubscription(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable Long customerId,
            @Valid @RequestBody RecurringPaymentRequest recurringPaymentRequest) {

        log.info("Creating subscription for customer: {} with correlation ID: {}", customerId, correlationId);
        PaymentResponse response = subscriptionService.createSubscription(
                customerId, recurringPaymentRequest, correlationId, idempotencyKey);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<Subscription> getSubscription(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String subscriptionId) {

        log.info("Getting subscription: {} with correlation ID: {}", subscriptionId, correlationId);
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with ID: " + subscriptionId));

        return ResponseEntity.ok(subscription);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Subscription>> getCustomerSubscriptions(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable Long customerId,
            @RequestParam(required = false) String status) {

        log.info("Getting subscriptions for customer: {} with correlation ID: {}", customerId, correlationId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        List<Subscription> subscriptions;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            subscriptions = subscriptionService.getActiveSubscriptionsByCustomer(customer);
        } else {
            subscriptions = subscriptionService.getSubscriptionsByCustomer(customer);
        }

        return ResponseEntity.ok(subscriptions);
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<PaymentResponse> cancelSubscription(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String subscriptionId,
            @RequestParam(required = false) String reason) {

        log.info("Cancelling subscription: {} with correlation ID: {}", subscriptionId, correlationId);
        PaymentResponse response = subscriptionService.cancelSubscription(subscriptionId, reason, correlationId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{subscriptionId}/amount")
    public ResponseEntity<PaymentResponse> updateSubscriptionAmount(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest updateRequest) {

        log.info("Updating amount for subscription: {} to {} with correlation ID: {}",
                subscriptionId, updateRequest.getAmount(), correlationId);
        PaymentResponse response = subscriptionService.updateSubscriptionAmount(
                subscriptionId, updateRequest.getAmount(), correlationId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{subscriptionId}/suspend")
    public ResponseEntity<PaymentResponse> suspendSubscription(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String subscriptionId,
            @RequestParam(required = false) String reason) {

        log.info("Suspending subscription: {} with correlation ID: {}", subscriptionId, correlationId);
        PaymentResponse response = subscriptionService.suspendSubscription(subscriptionId, reason, correlationId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{subscriptionId}/reactivate")
    public ResponseEntity<PaymentResponse> reactivateSubscription(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String subscriptionId) {

        log.info("Reactivating subscription: {} with correlation ID: {}", subscriptionId, correlationId);
        PaymentResponse response = subscriptionService.reactivateSubscription(subscriptionId, correlationId);

        return ResponseEntity.ok(response);
    }
}
