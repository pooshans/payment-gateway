package com.payment.gateway.service;

import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.PaymentMethod;
import com.payment.gateway.entity.Subscription;
import com.payment.gateway.exception.PaymentProcessingException;
import com.payment.gateway.exception.ResourceNotFoundException;
import com.payment.gateway.repository.CustomerRepository;
import com.payment.gateway.repository.PaymentMethodRepository;
import com.payment.gateway.repository.PaymentRepository;
import com.payment.gateway.repository.SubscriptionRepository;
import com.payment.gateway.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private Customer testCustomer;
    private Subscription testSubscription;
    private RecurringPaymentRequest recurringRequest;
    private String correlationId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        correlationId = UUID.randomUUID().toString();
        idempotencyKey = UUID.randomUUID().toString();

        testCustomer = Customer.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        PaymentMethod paymentMethod = PaymentMethod.builder()
                .id(1L)
                .customer(testCustomer)
                .type("CREDIT_CARD")
                .last4Digits("1234")
                .cardType("VISA")
                .expirationMonth("12")
                .expirationYear("2025")
                .isDefault(true)
                .build();

        testSubscription = Subscription.builder()
                .id(1L)
                .subscriptionId("SUB-123456")
                .customer(testCustomer)
                .paymentMethod(paymentMethod)
                .status("ACTIVE")
                .amount(new BigDecimal("19.99"))
                .currencyCode("USD")
                .name("Premium Plan")
                .description("Monthly premium subscription")
                .intervalLength(1)
                .intervalUnit("MONTH")
                .totalCycles(12)
                .completedCycles(3)
                .nextBillingDate(LocalDateTime.now().plusDays(10))
                .build();

        recurringRequest = new RecurringPaymentRequest();
        recurringRequest.setCardNumber("4111111111111111");
        recurringRequest.setExpirationDate("12/2025");
        recurringRequest.setCardSecurityCode("123");
        recurringRequest.setCardholderName("John Doe");
        recurringRequest.setAmount(new BigDecimal("19.99"));
        recurringRequest.setCurrencyCode("USD");
        recurringRequest.setSubscriptionName("Premium Plan");
        recurringRequest.setIntervalLength(1);
        recurringRequest.setIntervalUnit("MONTH");
        recurringRequest.setTotalOccurrences(12);
    }

    @Test
    void getSubscriptionById_shouldReturnSubscription_whenExists() {
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        Optional<Subscription> result = subscriptionService.getSubscriptionById("SUB-123456");

        assertTrue(result.isPresent());
        assertEquals("SUB-123456", result.get().getSubscriptionId());
    }

    @Test
    void getSubscriptionsByCustomer_shouldReturnList() {
        List<Subscription> subscriptions = Arrays.asList(testSubscription);
        when(subscriptionRepository.findByCustomer(testCustomer)).thenReturn(subscriptions);

        List<Subscription> result = subscriptionService.getSubscriptionsByCustomer(testCustomer);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SUB-123456", result.get(0).getSubscriptionId());
    }

    @Test
    void getActiveSubscriptionsByCustomer_shouldReturnActiveOnly() {
        List<Subscription> activeSubscriptions = Arrays.asList(testSubscription);
        when(subscriptionRepository.findByCustomerAndStatus(testCustomer, "ACTIVE")).thenReturn(activeSubscriptions);

        List<Subscription> result = subscriptionService.getActiveSubscriptionsByCustomer(testCustomer);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    void cancelSubscription_shouldUpdateStatus() {
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        PaymentResponse response = subscriptionService.cancelSubscription("SUB-123456", "Customer request",
                correlationId);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertEquals("CANCELLED", testSubscription.getStatus());
        assertNotNull(testSubscription.getCancelledAt());
        assertEquals("Customer request", testSubscription.getCancelReason());
        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    void cancelSubscription_shouldThrowException_whenAlreadyCancelled() {
        testSubscription.setStatus("CANCELLED");
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        assertThrows(PaymentProcessingException.class,
                () -> subscriptionService.cancelSubscription("SUB-123456", "Customer request", correlationId));
    }

    @Test
    void updateSubscriptionAmount_shouldUpdateAmount() {
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        BigDecimal newAmount = new BigDecimal("29.99");
        PaymentResponse response = subscriptionService.updateSubscriptionAmount("SUB-123456", newAmount, correlationId);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertEquals(newAmount, testSubscription.getAmount());
        assertEquals("UPDATED", response.getStatus());
    }

    @Test
    void suspendSubscription_shouldChangeStatus() {
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        PaymentResponse response = subscriptionService.suspendSubscription("SUB-123456", "Payment issue",
                correlationId);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertEquals("SUSPENDED", testSubscription.getStatus());
        assertEquals("SUSPENDED", response.getStatus());
    }

    @Test
    void reactivateSubscription_shouldChangeStatus() {
        testSubscription.setStatus("SUSPENDED");
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        PaymentResponse response = subscriptionService.reactivateSubscription("SUB-123456", correlationId);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertEquals("ACTIVE", testSubscription.getStatus());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void updateNextBillingDate_shouldUpdateDate() {
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        LocalDateTime newDate = LocalDateTime.now().plusMonths(1);
        Subscription updated = subscriptionService.updateNextBillingDate("SUB-123456", newDate);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertEquals(newDate, updated.getNextBillingDate());
    }

    @Test
    void processScheduledPayments_shouldUpdateCompletedCycles() {
        testSubscription.setNextBillingDate(LocalDateTime.now().minusDays(1));
        List<Subscription> dueSubscriptions = Arrays.asList(testSubscription);

        when(subscriptionRepository.findAll()).thenReturn(dueSubscriptions);

        int processed = subscriptionService.processScheduledPayments();

        assertEquals(1, processed);
        assertEquals(4, testSubscription.getCompletedCycles());
        assertTrue(testSubscription.getNextBillingDate().isAfter(LocalDateTime.now()));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }
}
