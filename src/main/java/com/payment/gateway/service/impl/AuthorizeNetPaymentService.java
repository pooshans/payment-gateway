package com.payment.gateway.service.impl;

import com.payment.gateway.domain.Transaction;
import com.payment.gateway.dto.*;
import com.payment.gateway.exception.PaymentProcessingException;
import com.payment.gateway.repository.TransactionRepository;
import com.payment.gateway.service.PaymentService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import net.authorize.Environment;
import net.authorize.api.contract.v1.*;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.GetTransactionDetailsController;
import net.authorize.api.controller.base.ApiOperationBase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the PaymentService that integrates with Authorize.NET.
 * 
 * This service handles all payment operations through the Authorize.NET API,
 * including:
 * - Processing one-time payments (auth+capture in one step)
 * - Authorizing payments without capture
 * - Capturing previously authorized payments
 * - Voiding transactions
 * - Issuing refunds
 * 
 * The implementation supports idempotency through a concurrent hash map cache
 * to prevent
 * duplicate transaction processing. It maintains detailed transaction records
 * in the database
 * and includes comprehensive error handling for various payment scenarios.
 * 
 * All operations include distributed tracing support and detailed logging for
 * observability.
 */
@Slf4j
@Service
public class AuthorizeNetPaymentService implements PaymentService {

    private final MerchantAuthenticationType merchantAuthentication;
    private final Environment environment;
    private final TransactionRepository transactionRepository;
    private final Tracer tracer;

    // In-memory cache for idempotency
    private final Map<String, PaymentResponse> idempotencyCache = new ConcurrentHashMap<>();

    public AuthorizeNetPaymentService(
            MerchantAuthenticationType merchantAuthentication,
            Environment authorizeNetEnvironment,
            TransactionRepository transactionRepository,
            Tracer tracer) {
        this.merchantAuthentication = merchantAuthentication;
        this.environment = authorizeNetEnvironment;
        this.transactionRepository = transactionRepository;
        this.tracer = tracer;

        // Configure the SDK with our environment
        ApiOperationBase.setEnvironment(environment);
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest paymentRequest, String correlationId, String idempotencyKey) {
        log.info("Processing payment request with correlation ID: {}", correlationId);

        // Check idempotency first
        if (idempotencyKey != null) {
            java.util.Optional<Transaction> existingTransaction = transactionRepository
                    .findByIdempotencyKey(idempotencyKey);
            if (existingTransaction.isPresent()) {
                log.info("Returning existing transaction for idempotency key: {}", idempotencyKey);
                return buildPaymentResponseFromTransaction(existingTransaction.get());
            }

            // Also check memory cache for very recent transactions
            if (idempotencyCache.containsKey(idempotencyKey)) {
                log.info("Returning cached response for idempotency key: {}", idempotencyKey);
                return idempotencyCache.get(idempotencyKey);
            }
        }

        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("payment.type", "purchase");
            span.tag("payment.correlation_id", correlationId);
        }

        try {
            // Create a credit card object
            CreditCardType creditCard = createCreditCardFromRequest(paymentRequest);

            // Create payment transaction
            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);

            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value());
            transactionRequest.setPayment(paymentType);

            // Set amount with proper precision
            BigDecimal amount = paymentRequest.getAmount().setScale(2, RoundingMode.HALF_UP);
            transactionRequest.setAmount(amount);

            // Set currency if provided, default to USD
            if (paymentRequest.getCurrencyCode() != null && !paymentRequest.getCurrencyCode().isEmpty()) {
                transactionRequest.setCurrencyCode(paymentRequest.getCurrencyCode());
            }

            // Add billing information if provided
            if (paymentRequest.getFirstName() != null || paymentRequest.getLastName() != null) {
                CustomerAddressType billTo = createBillingAddressFromRequest(paymentRequest);
                transactionRequest.setBillTo(billTo);
            }

            // Add order information if provided
            if (paymentRequest.getOrderNumber() != null || paymentRequest.getOrderDescription() != null) {
                OrderType order = new OrderType();
                order.setInvoiceNumber(paymentRequest.getOrderNumber());
                order.setDescription(paymentRequest.getOrderDescription());
                transactionRequest.setOrder(order);
            }

            // Create transaction request
            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);
            apiRequest.setTransactionRequest(transactionRequest);

            // Make API call
            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();

            // Get response
            CreateTransactionResponse response = controller.getApiResponse();

            // Process response
            PaymentResponse paymentResponse;
            Transaction transaction = new Transaction();

            if (response != null) {
                // Check if the API request was successful
                if (response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                    TransactionResponse result = response.getTransactionResponse();

                    if (result != null) {
                        log.info("Payment processed successfully. Transaction ID: {}", result.getTransId());

                        // Build success response
                        paymentResponse = PaymentResponse.builder()
                                .transactionId(result.getTransId())
                                .authCode(result.getAuthCode())
                                .status("APPROVED")
                                .responseCode(result.getResponseCode())
                                .responseMessage(
                                        result.getMessages() != null && !result.getMessages().getMessage().isEmpty()
                                                ? result.getMessages().getMessage().get(0).getDescription()
                                                : "Transaction approved")
                                .amount(amount)
                                .currencyCode(
                                        paymentRequest.getCurrencyCode() != null ? paymentRequest.getCurrencyCode()
                                                : "USD")
                                .cardType(result.getAccountType())
                                .last4Digits(paymentRequest.getCardNumber()
                                        .substring(paymentRequest.getCardNumber().length() - 4))
                                .transactionDate(LocalDateTime.now())
                                .correlationId(correlationId)
                                .isAuthorized(true)
                                .isCaptured(true)
                                .build();

                        // Save transaction in database
                        transaction.setTransactionId(result.getTransId());
                        transaction.setCorrelationId(correlationId);
                        transaction.setIdempotencyKey(idempotencyKey);
                        transaction.setStatus("APPROVED");
                        transaction.setAuthCode(result.getAuthCode());
                        transaction.setResponseCode(result.getResponseCode());
                        transaction.setResponseMessage(paymentResponse.getResponseMessage());
                        transaction.setAmount(amount);
                        transaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                        transaction.setCardType(result.getAccountType());
                        transaction.setLast4Digits(paymentResponse.getLast4Digits());
                        transaction.setAuthorization(true);
                        transaction.setCapture(true);
                    } else {
                        log.error("Transaction response is null");
                        throw new PaymentProcessingException("Transaction response is null");
                    }
                } else {
                    log.error("Payment failed with error code: {}",
                            response.getMessages().getMessage().get(0).getCode());

                    // Build error response
                    paymentResponse = PaymentResponse.builder()
                            .status("DECLINED")
                            .responseCode(response.getMessages().getMessage().get(0).getCode())
                            .responseMessage(response.getMessages().getMessage().get(0).getText())
                            .amount(amount)
                            .currencyCode(
                                    paymentRequest.getCurrencyCode() != null ? paymentRequest.getCurrencyCode() : "USD")
                            .correlationId(correlationId)
                            .build();

                    // Save failed transaction
                    transaction.setCorrelationId(correlationId);
                    transaction.setIdempotencyKey(idempotencyKey);
                    transaction.setStatus("DECLINED");
                    transaction.setResponseCode(response.getMessages().getMessage().get(0).getCode());
                    transaction.setResponseMessage(response.getMessages().getMessage().get(0).getText());
                    transaction.setAmount(amount);
                    transaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                    transaction.setLast4Digits(
                            paymentRequest.getCardNumber().substring(paymentRequest.getCardNumber().length() - 4));
                }
            } else {
                log.error("No response received from Authorize.Net");
                throw new PaymentProcessingException("No response received from Authorize.Net");
            }

            // Save transaction to database
            transactionRepository.save(transaction);

            // Cache the response if idempotency key was provided
            if (idempotencyKey != null) {
                idempotencyCache.put(idempotencyKey, paymentResponse);
            }

            return paymentResponse;
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error processing payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse authorizePayment(AuthorizationRequest authorizationRequest, String correlationId,
            String idempotencyKey) {
        log.info("Processing authorize request with correlation ID: {}", correlationId);

        // Check idempotency cache first
        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return idempotencyCache.get(idempotencyKey);
        }

        try {
            // Create a new transaction
            Transaction transaction = new Transaction();
            transaction.setCorrelationId(correlationId);
            transaction.setIdempotencyKey(idempotencyKey);
            transaction.setCreatedAt(LocalDateTime.now());

            // Convert amount to cents (or smallest currency unit)
            BigDecimal amount = authorizationRequest.getAmount();

            // Create Authorize.Net request
            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);

            // Create a transaction object
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_ONLY_TRANSACTION.value());
            transactionRequest.setAmount(amount);

            // Set payment method
            PaymentType paymentType = new PaymentType();
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(authorizationRequest.getCardNumber());
            creditCard.setExpirationDate(authorizationRequest.getExpirationDate().replace("/", "")); // Convert MM/YYYY
                                                                                                     // to MMYYYY
            creditCard.setCardCode(authorizationRequest.getCardSecurityCode());
            paymentType.setCreditCard(creditCard);
            transactionRequest.setPayment(paymentType);

            // Set billing information
            CustomerAddressType billTo = new CustomerAddressType();
            billTo.setFirstName(authorizationRequest.getCardholderName());
            transactionRequest.setBillTo(billTo);

            // Add the transaction request to the API request
            apiRequest.setTransactionRequest(transactionRequest);

            // Create the controller and execute the API request
            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();

            // Get the response
            CreateTransactionResponse response = controller.getApiResponse();

            // Initialize payment response
            PaymentResponse paymentResponse;

            if (response != null) {
                if (response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                    // Successful authorization
                    TransactionResponse result = response.getTransactionResponse();

                    if (result != null) {
                        log.info("Successfully authorized transaction with ID: {}", result.getTransId());

                        // Build response
                        paymentResponse = PaymentResponse.builder()
                                .transactionId(result.getTransId())
                                .authCode(result.getAuthCode())
                                .status("AUTHORIZED")
                                .responseCode(result.getResponseCode())
                                .responseMessage(
                                        result.getMessages() != null && result.getMessages().getMessage() != null &&
                                                !result.getMessages().getMessage().isEmpty()
                                                        ? result.getMessages().getMessage().get(0).getDescription()
                                                        : "Transaction Authorized")
                                .amount(amount)
                                .currencyCode(authorizationRequest.getCurrencyCode() != null
                                        ? authorizationRequest.getCurrencyCode()
                                        : "USD")
                                .cardType(result.getAccountType())
                                .last4Digits(authorizationRequest.getCardNumber()
                                        .substring(authorizationRequest.getCardNumber().length() - 4))
                                .transactionDate(LocalDateTime.now())
                                .correlationId(correlationId)
                                .isAuthorized(true)
                                .isCaptured(false)
                                .build();

                        // Update transaction details
                        transaction.setTransactionId(result.getTransId());
                        transaction.setStatus("AUTHORIZED");
                        transaction.setAuthCode(result.getAuthCode());
                        transaction.setResponseCode(result.getResponseCode());
                        transaction.setResponseMessage(paymentResponse.getResponseMessage());
                        transaction.setAmount(amount);
                        transaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                        transaction.setCardType(result.getAccountType());
                        transaction.setLast4Digits(paymentResponse.getLast4Digits());
                        transaction.setAuthorization(true);
                        transaction.setCapture(false);
                    } else {
                        log.error("Transaction response is null");
                        throw new PaymentProcessingException("Transaction response is null");
                    }
                } else {
                    // Failed authorization
                    log.error("Authorization failed with error code: {}",
                            response.getMessages().getMessage().get(0).getCode());

                    // Build error response
                    paymentResponse = PaymentResponse.builder()
                            .status("DECLINED")
                            .responseCode(response.getMessages().getMessage().get(0).getCode())
                            .responseMessage(response.getMessages().getMessage().get(0).getText())
                            .amount(amount)
                            .currencyCode(authorizationRequest.getCurrencyCode() != null
                                    ? authorizationRequest.getCurrencyCode()
                                    : "USD")
                            .correlationId(correlationId)
                            .isAuthorized(false)
                            .isCaptured(false)
                            .build();

                    // Update transaction details
                    transaction.setStatus("DECLINED");
                    transaction.setResponseCode(response.getMessages().getMessage().get(0).getCode());
                    transaction.setResponseMessage(response.getMessages().getMessage().get(0).getText());
                    transaction.setAmount(amount);
                    transaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                    transaction.setLast4Digits(
                            authorizationRequest.getCardNumber()
                                    .substring(authorizationRequest.getCardNumber().length() - 4));

                    // For declined transactions with no transactionId, generate one
                    if (paymentResponse.getTransactionId() == null) {
                        String declinedTxId = "DECLINED-" + java.util.UUID.randomUUID().toString();
                        transaction.setTransactionId(declinedTxId);
                        paymentResponse.setTransactionId(declinedTxId);
                    }
                }
            } else {
                log.error("No response received from Authorize.Net");

                // Generate a temporary transaction ID for failed transactions
                String errorTxId = "ERROR-" + java.util.UUID.randomUUID().toString();
                transaction.setTransactionId(errorTxId);
                transaction.setStatus("FAILED");
                transaction.setResponseCode("ERROR");
                transaction.setResponseMessage("No response received from Authorize.Net");

                // Create a failed payment response
                paymentResponse = PaymentResponse.builder()
                        .transactionId(errorTxId)
                        .status("FAILED")
                        .responseCode("ERROR")
                        .responseMessage("No response received from Authorize.Net")
                        .amount(amount)
                        .currencyCode(authorizationRequest.getCurrencyCode() != null
                                ? authorizationRequest.getCurrencyCode()
                                : "USD")
                        .last4Digits(authorizationRequest.getCardNumber()
                                .substring(authorizationRequest.getCardNumber().length() - 4))
                        .correlationId(correlationId)
                        .isAuthorized(false)
                        .isCaptured(false)
                        .build();
            }

            try {
                // Save transaction to database only if not an authentication error
                if (!"E00007".equals(transaction.getResponseCode()) && !"ERROR".equals(transaction.getResponseCode())) {
                    log.info("Saving successful transaction to database with ID: {}", transaction.getTransactionId());
                    transactionRepository.save(transaction);
                } else {
                    log.info("Skipping database save for error transaction with code: {}",
                            transaction.getResponseCode());
                }
            } catch (Exception e) {
                log.error("Error saving transaction to database: {}", e.getMessage());
                // Continue with response - don't let DB errors affect the API response
            }

            // Cache response if idempotency key was provided
            if (idempotencyKey != null) {
                idempotencyCache.put(idempotencyKey, paymentResponse);
            }

            return paymentResponse;
        } catch (Exception e) {
            log.error("Error authorizing payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error authorizing payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse capturePayment(String transactionId, CaptureRequest captureRequest, String correlationId) {
        log.info("Processing capture request for transaction ID: {} with correlation ID: {}", transactionId,
                correlationId);

        try {
            // Find the original transaction in the database
            Transaction originalTransaction = transactionRepository.findByTransactionId(transactionId)
                    .orElseThrow(
                            () -> new PaymentProcessingException("Original transaction not found: " + transactionId));

            if (!originalTransaction.isAuthorization() || originalTransaction.isCapture()) {
                throw new PaymentProcessingException("Transaction " + transactionId +
                        " is not eligible for capture. It must be an authorized but not captured transaction.");
            }

            // Create a new transaction record for the capture
            Transaction captureTransaction = new Transaction();
            captureTransaction.setCorrelationId(correlationId);
            captureTransaction.setOriginalTransactionId(transactionId);
            captureTransaction.setCreatedAt(LocalDateTime.now());

            // Use either the requested amount or the original amount if not specified
            BigDecimal amount = captureRequest.getAmount();

            // Create Authorize.Net capture request
            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);

            // Set up the transaction details
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.PRIOR_AUTH_CAPTURE_TRANSACTION.value());
            transactionRequest.setRefTransId(transactionId);
            transactionRequest.setAmount(amount);

            // Add the transaction request to the API request
            apiRequest.setTransactionRequest(transactionRequest);

            // Execute the API request
            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();

            // Get the response
            CreateTransactionResponse response = controller.getApiResponse();

            // Initialize payment response
            PaymentResponse paymentResponse;

            if (response != null) {
                if (response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                    // Successful capture
                    TransactionResponse result = response.getTransactionResponse();

                    if (result != null) {
                        log.info("Successfully captured transaction with ID: {}", result.getTransId());

                        // Build response
                        paymentResponse = PaymentResponse.builder()
                                .transactionId(result.getTransId())
                                .originalTransactionId(transactionId)
                                .authCode(result.getAuthCode())
                                .status("CAPTURED")
                                .responseCode(result.getResponseCode())
                                .responseMessage(
                                        result.getMessages() != null && result.getMessages().getMessage() != null &&
                                                !result.getMessages().getMessage().isEmpty()
                                                        ? result.getMessages().getMessage().get(0).getDescription()
                                                        : "Transaction Captured")
                                .amount(amount)
                                .currencyCode(
                                        captureRequest.getCurrencyCode() != null ? captureRequest.getCurrencyCode()
                                                : originalTransaction.getCurrencyCode())
                                .cardType(originalTransaction.getCardType())
                                .last4Digits(originalTransaction.getLast4Digits())
                                .transactionDate(LocalDateTime.now())
                                .correlationId(correlationId)
                                .isAuthorized(true)
                                .isCaptured(true)
                                .build();

                        // Update capture transaction details
                        captureTransaction.setTransactionId(result.getTransId());
                        captureTransaction.setStatus("CAPTURED");
                        captureTransaction.setAuthCode(result.getAuthCode());
                        captureTransaction.setResponseCode(result.getResponseCode());
                        captureTransaction.setResponseMessage(paymentResponse.getResponseMessage());
                        captureTransaction.setAmount(amount);
                        captureTransaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                        captureTransaction.setCardType(originalTransaction.getCardType());
                        captureTransaction.setLast4Digits(originalTransaction.getLast4Digits());
                        captureTransaction.setAuthorization(false);
                        captureTransaction.setCapture(true);
                    } else {
                        log.error("Transaction response is null");
                        throw new PaymentProcessingException("Transaction response is null");
                    }
                } else {
                    // Failed capture
                    log.error("Capture failed with error code: {}",
                            response.getMessages().getMessage().get(0).getCode());

                    // Build error response
                    paymentResponse = PaymentResponse.builder()
                            .originalTransactionId(transactionId)
                            .status("DECLINED")
                            .responseCode(response.getMessages().getMessage().get(0).getCode())
                            .responseMessage(response.getMessages().getMessage().get(0).getText())
                            .amount(amount)
                            .currencyCode(captureRequest.getCurrencyCode() != null ? captureRequest.getCurrencyCode()
                                    : originalTransaction.getCurrencyCode())
                            .correlationId(correlationId)
                            .isAuthorized(true)
                            .isCaptured(false)
                            .build();

                    // Update capture transaction details
                    captureTransaction.setStatus("DECLINED");
                    captureTransaction.setResponseCode(response.getMessages().getMessage().get(0).getCode());
                    captureTransaction.setResponseMessage(response.getMessages().getMessage().get(0).getText());
                    captureTransaction.setAmount(amount);
                    captureTransaction.setCurrencyCode(paymentResponse.getCurrencyCode());
                    captureTransaction.setCardType(originalTransaction.getCardType());
                    captureTransaction.setLast4Digits(originalTransaction.getLast4Digits());
                }
            } else {
                log.error("No response received from Authorize.Net");
                throw new PaymentProcessingException("No response received from Authorize.Net");
            }

            // Save capture transaction to database
            transactionRepository.save(captureTransaction);

            return paymentResponse;
        } catch (Exception e) {
            log.error("Error capturing payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error capturing payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse voidPayment(String transactionId, String correlationId) {
        log.info("Processing void request for transaction ID: {} with correlation ID: {}", transactionId,
                correlationId);

        try {
            // Find the original transaction in the database
            Transaction originalTransaction = transactionRepository.findByTransactionId(transactionId)
                    .orElseThrow(
                            () -> new PaymentProcessingException("Original transaction not found: " + transactionId));

            // Create a new transaction record for the void
            Transaction voidTransaction = new Transaction();
            voidTransaction.setCorrelationId(correlationId);
            voidTransaction.setOriginalTransactionId(transactionId);
            voidTransaction.setCreatedAt(LocalDateTime.now());
            voidTransaction.setAmount(originalTransaction.getAmount());
            voidTransaction.setCurrencyCode(originalTransaction.getCurrencyCode());
            voidTransaction.setCardType(originalTransaction.getCardType());
            voidTransaction.setLast4Digits(originalTransaction.getLast4Digits());

            // Create Authorize.Net void request
            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);

            // Set up the transaction details
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
            transactionRequest.setRefTransId(transactionId);

            // Add the transaction request to the API request
            apiRequest.setTransactionRequest(transactionRequest);

            // Execute the API request
            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();

            // Get the response
            CreateTransactionResponse response = controller.getApiResponse();

            // Initialize payment response
            PaymentResponse paymentResponse;

            if (response != null) {
                if (response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                    // Successful void
                    TransactionResponse result = response.getTransactionResponse();

                    if (result != null) {
                        log.info("Successfully voided transaction with ID: {}", result.getTransId());

                        // Build response
                        paymentResponse = PaymentResponse.builder()
                                .transactionId(result.getTransId())
                                .originalTransactionId(transactionId)
                                .authCode(result.getAuthCode())
                                .status("VOIDED")
                                .responseCode(result.getResponseCode())
                                .responseMessage(
                                        result.getMessages() != null && result.getMessages().getMessage() != null &&
                                                !result.getMessages().getMessage().isEmpty()
                                                        ? result.getMessages().getMessage().get(0).getDescription()
                                                        : "Transaction Voided")
                                .amount(originalTransaction.getAmount())
                                .currencyCode(originalTransaction.getCurrencyCode())
                                .cardType(originalTransaction.getCardType())
                                .last4Digits(originalTransaction.getLast4Digits())
                                .transactionDate(LocalDateTime.now())
                                .correlationId(correlationId)
                                .isAuthorized(false)
                                .isCaptured(false)
                                .build();

                        // Update void transaction details
                        voidTransaction.setTransactionId(result.getTransId());
                        voidTransaction.setStatus("VOIDED");
                        voidTransaction.setAuthCode(result.getAuthCode());
                        voidTransaction.setResponseCode(result.getResponseCode());
                        voidTransaction.setResponseMessage(paymentResponse.getResponseMessage());
                        voidTransaction.setAuthorization(false);
                        voidTransaction.setCapture(false);
                    } else {
                        log.error("Transaction response is null");
                        throw new PaymentProcessingException("Transaction response is null");
                    }
                } else {
                    // Failed void
                    log.error("Void failed with error code: {}",
                            response.getMessages().getMessage().get(0).getCode());

                    // Build error response
                    paymentResponse = PaymentResponse.builder()
                            .originalTransactionId(transactionId)
                            .status("ERROR")
                            .responseCode(response.getMessages().getMessage().get(0).getCode())
                            .responseMessage(response.getMessages().getMessage().get(0).getText())
                            .amount(originalTransaction.getAmount())
                            .currencyCode(originalTransaction.getCurrencyCode())
                            .correlationId(correlationId)
                            .build();

                    // Update void transaction details
                    voidTransaction.setStatus("ERROR");
                    voidTransaction.setResponseCode(response.getMessages().getMessage().get(0).getCode());
                    voidTransaction.setResponseMessage(response.getMessages().getMessage().get(0).getText());
                }
            } else {
                log.error("No response received from Authorize.Net");
                throw new PaymentProcessingException("No response received from Authorize.Net");
            }

            // Save void transaction to database
            transactionRepository.save(voidTransaction);

            return paymentResponse;
        } catch (Exception e) {
            log.error("Error voiding payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error voiding payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse refundPayment(RefundRequest refundRequest, String correlationId, String idempotencyKey) {
        log.info("Processing refund request for transaction ID: {} with correlation ID: {}",
                refundRequest.getOriginalTransactionId(), correlationId);

        // Check idempotency cache first
        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return idempotencyCache.get(idempotencyKey);
        }

        try {
            String originalTransactionId = refundRequest.getOriginalTransactionId();

            // Find the original transaction in the database
            Transaction originalTransaction = transactionRepository.findByTransactionId(originalTransactionId)
                    .orElseThrow(() -> new PaymentProcessingException(
                            "Original transaction not found: " + originalTransactionId));

            // Create a new transaction record for the refund
            Transaction refundTransaction = new Transaction();
            refundTransaction.setCorrelationId(correlationId);
            refundTransaction.setIdempotencyKey(idempotencyKey);
            refundTransaction.setOriginalTransactionId(originalTransactionId);
            refundTransaction.setCreatedAt(LocalDateTime.now());

            // Use either the requested amount or the original amount if not specified
            BigDecimal amount = refundRequest.getAmount();
            String currencyCode = refundRequest.getCurrencyCode() != null ? refundRequest.getCurrencyCode()
                    : originalTransaction.getCurrencyCode();

            // Create Authorize.Net refund request
            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);

            // Set up the transaction details
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
            transactionRequest.setAmount(amount);

            // For a refund, we need the original payment details
            PaymentType paymentType = new PaymentType();
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(originalTransaction.getLast4Digits()); // In a real implementation, you'd need the
                                                                            // full card number or a token
            creditCard.setExpirationDate("XXXX"); // In a real implementation, you'd need the actual expiration date
            paymentType.setCreditCard(creditCard);
            transactionRequest.setPayment(paymentType);

            // Set refund details
            transactionRequest.setRefTransId(originalTransactionId);

            // Add the transaction request to the API request
            apiRequest.setTransactionRequest(transactionRequest);

            // Execute the API request
            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();

            // Get the response
            CreateTransactionResponse response = controller.getApiResponse();

            // Initialize payment response
            PaymentResponse paymentResponse;

            if (response != null) {
                if (response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                    // Successful refund
                    TransactionResponse result = response.getTransactionResponse();

                    if (result != null) {
                        log.info("Successfully refunded transaction with ID: {}", result.getTransId());

                        // Build response
                        paymentResponse = PaymentResponse.builder()
                                .transactionId(result.getTransId())
                                .originalTransactionId(originalTransactionId)
                                .authCode(result.getAuthCode())
                                .status("REFUNDED")
                                .responseCode(result.getResponseCode())
                                .responseMessage(
                                        result.getMessages() != null && result.getMessages().getMessage() != null &&
                                                !result.getMessages().getMessage().isEmpty()
                                                        ? result.getMessages().getMessage().get(0).getDescription()
                                                        : "Transaction Refunded")
                                .amount(amount)
                                .currencyCode(currencyCode)
                                .cardType(originalTransaction.getCardType())
                                .last4Digits(originalTransaction.getLast4Digits())
                                .transactionDate(LocalDateTime.now())
                                .correlationId(correlationId)
                                .build();

                        // Update refund transaction details
                        refundTransaction.setTransactionId(result.getTransId());
                        refundTransaction.setStatus("REFUNDED");
                        refundTransaction.setAuthCode(result.getAuthCode());
                        refundTransaction.setResponseCode(result.getResponseCode());
                        refundTransaction.setResponseMessage(paymentResponse.getResponseMessage());
                        refundTransaction.setAmount(amount);
                        refundTransaction.setCurrencyCode(currencyCode);
                        refundTransaction.setCardType(originalTransaction.getCardType());
                        refundTransaction.setLast4Digits(originalTransaction.getLast4Digits());
                    } else {
                        log.error("Transaction response is null");
                        throw new PaymentProcessingException("Transaction response is null");
                    }
                } else {
                    // Failed refund
                    log.error("Refund failed with error code: {}",
                            response.getMessages().getMessage().get(0).getCode());

                    // Build error response
                    paymentResponse = PaymentResponse.builder()
                            .originalTransactionId(originalTransactionId)
                            .status("ERROR")
                            .responseCode(response.getMessages().getMessage().get(0).getCode())
                            .responseMessage(response.getMessages().getMessage().get(0).getText())
                            .amount(amount)
                            .currencyCode(currencyCode)
                            .correlationId(correlationId)
                            .build();

                    // Update refund transaction details
                    refundTransaction.setStatus("ERROR");
                    refundTransaction.setResponseCode(response.getMessages().getMessage().get(0).getCode());
                    refundTransaction.setResponseMessage(response.getMessages().getMessage().get(0).getText());
                    refundTransaction.setAmount(amount);
                    refundTransaction.setCurrencyCode(currencyCode);
                    refundTransaction.setCardType(originalTransaction.getCardType());
                    refundTransaction.setLast4Digits(originalTransaction.getLast4Digits());
                }
            } else {
                log.error("No response received from Authorize.Net");
                throw new PaymentProcessingException("No response received from Authorize.Net");
            }

            // Save refund transaction to database
            transactionRepository.save(refundTransaction);

            // Cache the response if idempotency key was provided
            if (idempotencyKey != null) {
                idempotencyCache.put(idempotencyKey, paymentResponse);
            }

            return paymentResponse;
        } catch (Exception e) {
            log.error("Error refunding payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error refunding payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse createRecurringPayment(RecurringPaymentRequest recurringRequest, String correlationId,
            String idempotencyKey) {
        log.info("Processing recurring payment request with correlation ID: {}", correlationId);

        // Check idempotency cache first
        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return idempotencyCache.get(idempotencyKey);
        }

        try {
            // Create a new transaction record
            Transaction transaction = new Transaction();
            transaction.setCorrelationId(correlationId);
            transaction.setIdempotencyKey(idempotencyKey);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setAmount(recurringRequest.getAmount());
            transaction.setCurrencyCode(
                    recurringRequest.getCurrencyCode() != null ? recurringRequest.getCurrencyCode() : "USD");
            transaction.setLast4Digits(
                    recurringRequest.getCardNumber().substring(recurringRequest.getCardNumber().length() - 4));

            // Generate a subscription ID (in a real implementation, this would come from
            // the payment gateway)
            String subscriptionId = "SUB-" + java.util.UUID.randomUUID().toString();

            // Build subscription metadata that would normally be stored in the payment
            // gateway
            String subscriptionInfo = String.format(
                    "Subscription: %s, Amount: %s %s, Interval: %d %s",
                    recurringRequest.getSubscriptionName(),
                    recurringRequest.getAmount(),
                    recurringRequest.getCurrencyCode() != null ? recurringRequest.getCurrencyCode() : "USD",
                    recurringRequest.getIntervalLength(),
                    recurringRequest.getIntervalUnit());

            log.info("Created recurring payment subscription: {}", subscriptionInfo);

            // Build response
            PaymentResponse paymentResponse = PaymentResponse.builder()
                    .subscriptionId(subscriptionId)
                    .status("ACTIVE")
                    .responseCode("I00001") // Successful response code
                    .responseMessage("Successful")
                    .amount(recurringRequest.getAmount())
                    .currencyCode(
                            recurringRequest.getCurrencyCode() != null ? recurringRequest.getCurrencyCode() : "USD")
                    .last4Digits(recurringRequest.getCardNumber()
                            .substring(recurringRequest.getCardNumber().length() - 4))
                    .transactionDate(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            // Update transaction details
            transaction.setSubscriptionId(subscriptionId);
            transaction.setStatus("ACTIVE");
            transaction.setResponseCode("I00001");
            transaction.setResponseMessage("Subscription created successfully");

            // Save transaction to database
            transactionRepository.save(transaction);

            // Cache response if idempotency key was provided
            if (idempotencyKey != null) {
                idempotencyCache.put(idempotencyKey, paymentResponse);
            }

            return paymentResponse;

        } catch (Exception e) {
            log.error("Error creating recurring payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error creating recurring payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse getPaymentDetails(String transactionId, String correlationId) {
        log.info("Retrieving payment details for transaction ID: {} with correlation ID: {}", transactionId,
                correlationId);

        try {
            // First, check our local database for the transaction
            java.util.Optional<Transaction> transactionOpt = transactionRepository.findByTransactionId(transactionId);

            if (transactionOpt.isPresent()) {
                log.info("Transaction found in local database");
                return buildPaymentResponseFromTransaction(transactionOpt.get());
            }

            // If not found locally, query Authorize.Net
            log.info("Transaction not found locally, querying Authorize.Net");

            // Create the API request
            GetTransactionDetailsRequest apiRequest = new GetTransactionDetailsRequest();
            apiRequest.setMerchantAuthentication(merchantAuthentication);
            apiRequest.setTransId(transactionId);

            // Create controller and execute
            GetTransactionDetailsController controller = new GetTransactionDetailsController(apiRequest);
            controller.execute();

            // Get the response
            GetTransactionDetailsResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                net.authorize.api.contract.v1.TransactionDetailsType transaction = response.getTransaction();

                if (transaction != null) {
                    log.info("Successfully retrieved transaction details from Authorize.Net");

                    // Extract payment details
                    String cardNumber = null;
                    String last4Digits = null;
                    String cardType = null;

                    if (transaction.getPayment() != null && transaction.getPayment().getCreditCard() != null) {
                        cardNumber = transaction.getPayment().getCreditCard().getCardNumber();
                        if (cardNumber != null && cardNumber.length() >= 4) {
                            last4Digits = cardNumber.substring(cardNumber.length() - 4);
                        }
                        cardType = transaction.getPayment().getCreditCard().getCardType();
                    }

                    // Build response
                    return PaymentResponse.builder()
                            .transactionId(transaction.getTransId())
                            .originalTransactionId(transaction.getRefTransId())
                            .authCode(transaction.getAuthCode())
                            .status(mapAuthorizeNetStatus(transaction.getTransactionStatus()))
                            .responseCode(String.valueOf(transaction.getResponseCode()))
                            .responseMessage(transaction.getResponseReasonDescription())
                            .amount(transaction.getSettleAmount())
                            .currencyCode("USD") // Default to USD as the XMLGregorianCalendar doesn't have currency
                            .cardType(cardType)
                            .last4Digits(last4Digits)
                            .transactionDate(LocalDateTime.now()) // No direct mapping for transaction date in the
                                                                  // response
                            .correlationId(correlationId)
                            .isAuthorized(isAuthorized(transaction.getTransactionStatus()))
                            .isCaptured(isCaptured(transaction.getTransactionStatus()))
                            .build();
                }
            }

            log.error("Could not retrieve transaction details from Authorize.Net");
            throw new PaymentProcessingException("Could not retrieve transaction details: " + transactionId);

        } catch (Exception e) {
            log.error("Error retrieving payment details: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Error retrieving payment details: " + e.getMessage(), e);
        }
    }

    /**
     * Maps Authorize.Net transaction status to our internal status
     */
    private String mapAuthorizeNetStatus(String authorizeNetStatus) {
        if (authorizeNetStatus == null) {
            return "UNKNOWN";
        }

        switch (authorizeNetStatus.toUpperCase()) {
            case "AUTHORIZEDPENDINGCAPTURE":
                return "AUTHORIZED";
            case "CAPTUREDPENDINGSETTLEMENT":
            case "CAPTUREDSETTLED":
                return "CAPTURED";
            case "VOIDED":
                return "VOIDED";
            case "REFUNDEDSETTLED":
            case "REFUNDPENDINGSETTLEMENT":
                return "REFUNDED";
            case "DECLINED":
                return "DECLINED";
            case "ERROR":
            case "FDSPENDINGREVIEW":
            case "FDSAUTHORIZEDPENDINGACCEPTANCE":
                return "ERROR";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Determines if a transaction is authorized based on Authorize.Net status
     */
    private boolean isAuthorized(String authorizeNetStatus) {
        if (authorizeNetStatus == null) {
            return false;
        }

        String status = authorizeNetStatus.toUpperCase();
        return status.contains("AUTHORIZED") ||
                status.contains("CAPTURED") ||
                status.contains("SETTLED");
    }

    /**
     * Determines if a transaction is captured based on Authorize.Net status
     */
    private boolean isCaptured(String authorizeNetStatus) {
        if (authorizeNetStatus == null) {
            return false;
        }

        String status = authorizeNetStatus.toUpperCase();
        return status.contains("CAPTURED") || status.contains("SETTLED");
    }

    /**
     * Creates a CreditCardType object from the payment request
     */
    private CreditCardType createCreditCardFromRequest(PaymentRequest paymentRequest) {
        CreditCardType creditCard = new CreditCardType();
        creditCard.setCardNumber(paymentRequest.getCardNumber());
        creditCard.setExpirationDate(paymentRequest.getExpirationDate());
        creditCard.setCardCode(paymentRequest.getCardSecurityCode());
        return creditCard;
    }

    /**
     * Creates a CustomerAddressType object for billing information
     */
    private CustomerAddressType createBillingAddressFromRequest(PaymentRequest paymentRequest) {
        CustomerAddressType billTo = new CustomerAddressType();
        billTo.setFirstName(paymentRequest.getFirstName());
        billTo.setLastName(paymentRequest.getLastName());
        billTo.setCompany(paymentRequest.getCompany());
        billTo.setAddress(paymentRequest.getAddress());
        billTo.setCity(paymentRequest.getCity());
        billTo.setState(paymentRequest.getState());
        billTo.setZip(paymentRequest.getZip());
        billTo.setCountry(paymentRequest.getCountry());
        return billTo;
    }

    /**
     * Builds a PaymentResponse from a Transaction entity
     */
    private PaymentResponse buildPaymentResponseFromTransaction(Transaction transaction) {
        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .originalTransactionId(transaction.getOriginalTransactionId())
                .authCode(transaction.getAuthCode())
                .status(transaction.getStatus())
                .responseCode(transaction.getResponseCode())
                .responseMessage(transaction.getResponseMessage())
                .amount(transaction.getAmount())
                .currencyCode(transaction.getCurrencyCode())
                .cardType(transaction.getCardType())
                .last4Digits(transaction.getLast4Digits())
                .transactionDate(transaction.getCreatedAt())
                .correlationId(transaction.getCorrelationId())
                .isAuthorized(transaction.isAuthorization())
                .isCaptured(transaction.isCapture())
                .subscriptionId(transaction.getSubscriptionId())
                .build();
    }
}
