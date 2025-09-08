package com.payment.gateway.service;

import com.payment.gateway.dto.*;

/**
 * Service interface for payment processing operations.
 * 
 * This service defines the core payment operations supported by the Payment
 * Gateway,
 * including one-time payments, authorization and capture flows, voiding
 * transactions,
 * and processing refunds.
 * 
 * All methods take a correlationId parameter for distributed tracing, and
 * payment
 * creation methods support idempotency keys to prevent duplicate transactions.
 * Implementations integrate with the Authorize.NET payment processor while
 * providing
 * abstraction for potential future payment provider changes.
 */
public interface PaymentService {

    /**
     * Process a one-time payment
     */
    PaymentResponse processPayment(PaymentRequest paymentRequest, String correlationId, String idempotencyKey);

    /**
     * Authorize a payment without capturing it
     */
    PaymentResponse authorizePayment(AuthorizationRequest authorizationRequest, String correlationId,
            String idempotencyKey);

    /**
     * Capture a previously authorized payment
     */
    PaymentResponse capturePayment(String transactionId, CaptureRequest captureRequest, String correlationId);

    /**
     * Void a transaction that has been authorized but not settled
     */
    PaymentResponse voidPayment(String transactionId, String correlationId);

    /**
     * Refund a previously settled transaction
     */
    PaymentResponse refundPayment(RefundRequest refundRequest, String correlationId, String idempotencyKey);

    /**
     * Create a recurring payment subscription
     */
    PaymentResponse createRecurringPayment(RecurringPaymentRequest recurringRequest, String correlationId,
            String idempotencyKey);

    /**
     * Get payment transaction details
     */
    PaymentResponse getPaymentDetails(String transactionId, String correlationId);
}
