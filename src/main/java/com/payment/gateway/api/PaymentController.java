package com.payment.gateway.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.payment.gateway.dto.PaymentRequest;
import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.AuthorizationRequest;
import com.payment.gateway.dto.CaptureRequest;
import com.payment.gateway.dto.RefundRequest;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.service.PaymentService;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST controller that handles all payment-related operations.
 * 
 * This controller provides endpoints for processing one-time payments,
 * authorizing transactions,
 * capturing authorized payments, voiding transactions, and processing refunds.
 * 
 * All endpoints use correlation IDs for request tracing and support idempotency
 * to prevent
 * duplicate payment processing. The controller delegates actual payment
 * processing to the
 * PaymentService, which integrates with the Authorize.NET payment gateway.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/purchase")
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity.ok(paymentService.processPayment(paymentRequest, requestCorrelationId, idempotencyKey));
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorizePayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AuthorizationRequest authorizationRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity
                .ok(paymentService.authorizePayment(authorizationRequest, requestCorrelationId, idempotencyKey));
    }

    @PostMapping("/capture/{transactionId}")
    public ResponseEntity<PaymentResponse> capturePayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String transactionId,
            @Valid @RequestBody CaptureRequest captureRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity.ok(paymentService.capturePayment(transactionId, captureRequest, requestCorrelationId));
    }

    @PostMapping("/cancel/{transactionId}")
    public ResponseEntity<PaymentResponse> voidPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String transactionId) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity.ok(paymentService.voidPayment(transactionId, requestCorrelationId));
    }

    @PostMapping("/refund/{transactionId}")
    public ResponseEntity<PaymentResponse> refundPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String transactionId,
            @Valid @RequestBody RefundRequest refundRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        // Set the transaction ID from the path
        refundRequest.setOriginalTransactionId(transactionId);
        return ResponseEntity.ok(paymentService.refundPayment(refundRequest, requestCorrelationId, idempotencyKey));
    }

    @PostMapping("/partial-refund/{transactionId}")
    public ResponseEntity<PaymentResponse> partialRefundPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String transactionId,
            @Valid @RequestBody RefundRequest refundRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        // Set the transaction ID from the path
        refundRequest.setOriginalTransactionId(transactionId);
        return ResponseEntity.ok(paymentService.refundPayment(refundRequest, requestCorrelationId, idempotencyKey));
    }

    @PostMapping("/recurring")
    public ResponseEntity<PaymentResponse> createRecurringPayment(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecurringPaymentRequest recurringRequest) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity
                .ok(paymentService.createRecurringPayment(recurringRequest, requestCorrelationId, idempotencyKey));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<PaymentResponse> getPaymentDetails(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String transactionId) {

        String requestCorrelationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        return ResponseEntity.ok(paymentService.getPaymentDetails(transactionId, requestCorrelationId));
    }
}
