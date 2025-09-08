package com.payment.gateway.service.impl;

import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Payment;
import com.payment.gateway.entity.PaymentMethod;
import com.payment.gateway.entity.Subscription;
import com.payment.gateway.exception.PaymentProcessingException;
import com.payment.gateway.repository.CustomerRepository;
import com.payment.gateway.repository.PaymentMethodRepository;
import com.payment.gateway.repository.PaymentRepository;
import com.payment.gateway.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
public class SubscriptionServiceImplTest {

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

    @Captor
    private ArgumentCaptor<Subscription> subscriptionCaptor;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private Customer testCustomer;
    private PaymentMethod testPaymentMethod;
    private Subscription testSubscription;
    private RecurringPaymentRequest recurringRequest;
    private String correlationId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        // Initialize test data
        correlationId = UUID.randomUUID().toString();
        idempotencyKey = UUID.randomUUID().toString();

        testCustomer = Customer.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        testPaymentMethod = PaymentMethod.builder()
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
                .paymentMethod(testPaymentMethod)
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
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        // Act
        Optional<Subscription> result = subscriptionService.getSubscriptionById("SUB-123456");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("SUB-123456", result.get().getSubscriptionId());
        assertEquals("Premium Plan", result.get().getName());
    }

    @Test
    void getSubscriptionById_shouldReturnEmpty_whenNotExists() {
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("NON-EXISTENT")).thenReturn(Optional.empty());

        // Act
        Optional<Subscription> result = subscriptionService.getSubscriptionById("NON-EXISTENT");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void getSubscriptionsByCustomer_shouldReturnList() {
        // Arrange
        List<Subscription> subscriptions = Arrays.asList(testSubscription);
        when(subscriptionRepository.findByCustomer(testCustomer)).thenReturn(subscriptions);

        // Act
        List<Subscription> result = subscriptionService.getSubscriptionsByCustomer(testCustomer);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SUB-123456", result.get(0).getSubscriptionId());
    }

    @Test
    void getActiveSubscriptionsByCustomer_shouldReturnActiveOnly() {
        // Arrange
        List<Subscription> activeSubscriptions = Arrays.asList(testSubscription);
        when(subscriptionRepository.findByCustomerAndStatus(testCustomer, "ACTIVE")).thenReturn(activeSubscriptions);

        // Act
        List<Subscription> result = subscriptionService.getActiveSubscriptionsByCustomer(testCustomer);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    void cancelSubscription_shouldUpdateStatus() {
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        // Act
        PaymentResponse response = subscriptionService.cancelSubscription("SUB-123456", "Customer request",
                correlationId);

        // Assert
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals("CANCELLED", savedSubscription.getStatus());
        assertNotNull(savedSubscription.getCancelledAt());
        assertEquals("Customer request", savedSubscription.getCancelReason());
        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    void cancelSubscription_shouldThrowException_whenAlreadyCancelled() {
        // Arrange
        testSubscription.setStatus("CANCELLED");
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        // Act & Assert
        assertThrows(PaymentProcessingException.class,
                () -> subscriptionService.cancelSubscription("SUB-123456", "Customer request", correlationId));
    }

    @Test
    void updateSubscriptionAmount_shouldUpdateAmount() {
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));
        BigDecimal newAmount = new BigDecimal("29.99");

        // Act
        PaymentResponse result = subscriptionService.updateSubscriptionAmount("SUB-123456", newAmount, correlationId);

        // Assert
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals(newAmount, savedSubscription.getAmount());
        assertEquals("SUB-123456", result.getSubscriptionId());
    }

    @Test
    void suspendSubscription_shouldChangeStatus() {
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        // Act
        PaymentResponse response = subscriptionService.suspendSubscription("SUB-123456", "Billing issue",
                correlationId);

        // Assert
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals("SUSPENDED", savedSubscription.getStatus());
        assertEquals("Billing issue", savedSubscription.getCancelReason());
        assertEquals("SUSPENDED", response.getStatus());
    }

    @Test
    void reactivateSubscription_shouldChangeStatus() {
        // Arrange
        testSubscription.setStatus("SUSPENDED");
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));

        // Act
        PaymentResponse response = subscriptionService.reactivateSubscription("SUB-123456", correlationId);

        // Assert
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals("ACTIVE", savedSubscription.getStatus());
        assertNull(savedSubscription.getCancelReason());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void updateNextBillingDate_shouldUpdateDate() {
        // Arrange
        when(subscriptionRepository.findBySubscriptionId("SUB-123456")).thenReturn(Optional.of(testSubscription));
        LocalDateTime newDate = LocalDateTime.now().plusMonths(1);

        // Act
        Subscription updated = subscriptionService.updateNextBillingDate("SUB-123456", newDate);

        // Assert
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals(newDate, savedSubscription.getNextBillingDate());
        assertEquals(newDate, updated.getNextBillingDate());
    }

    @Test
    void processScheduledPayments_shouldUpdateCompletedCycles() {
        // Arrange
        testSubscription.setNextBillingDate(LocalDateTime.now().minusDays(1)); // Due payment
        List<Subscription> dueSubscriptions = Arrays.asList(testSubscription);

        when(subscriptionRepository.findAll()).thenReturn(dueSubscriptions);

        // Act
        int processed = subscriptionService.processScheduledPayments();

        // Assert
        assertEquals(1, processed);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();

        assertEquals(4, savedSubscription.getCompletedCycles()); // Was 3, now 4
        assertTrue(savedSubscription.getNextBillingDate().isAfter(LocalDateTime.now()));
    }
}
