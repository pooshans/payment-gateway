package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.dto.SubscriptionUpdateRequest;
import com.payment.gateway.entity.Customer;
import com.payment.gateway.entity.Subscription;
import com.payment.gateway.repository.CustomerRepository;
import com.payment.gateway.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private CustomerRepository customerRepository;

    private Customer testCustomer;
    private Subscription testSubscription;
    private RecurringPaymentRequest recurringRequest;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        testSubscription = Subscription.builder()
                .id(1L)
                .subscriptionId("SUB-123456")
                .customer(testCustomer)
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

        paymentResponse = PaymentResponse.builder()
                .transactionId("PAY-123456")
                .status("CAPTURED")
                .responseMessage("Payment successful")
                .amount(new BigDecimal("19.99"))
                .currencyCode("USD")
                .build();
    }

    @Test
    void createSubscription_shouldReturnOk() throws Exception {
        when(subscriptionService.createSubscription(eq(1L), any(RecurringPaymentRequest.class), anyString(),
                anyString()))
                .thenReturn(paymentResponse);

        mockMvc.perform(post("/subscriptions/customer/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key")
                .content(objectMapper.writeValueAsString(recurringRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is("PAY-123456")))
                .andExpect(jsonPath("$.status", is("CAPTURED")));
    }

    @Test
    void getSubscription_shouldReturnSubscription() throws Exception {
        when(subscriptionService.getSubscriptionById("SUB-123456")).thenReturn(Optional.of(testSubscription));

        mockMvc.perform(get("/subscriptions/SUB-123456")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId", is("SUB-123456")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void getSubscription_shouldReturn404_whenNotFound() throws Exception {
        when(subscriptionService.getSubscriptionById("SUB-NOT-FOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/subscriptions/SUB-NOT-FOUND")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCustomerSubscriptions_shouldReturnList() throws Exception {
        List<Subscription> subscriptions = Arrays.asList(testSubscription);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(subscriptionService.getSubscriptionsByCustomer(testCustomer)).thenReturn(subscriptions);

        mockMvc.perform(get("/subscriptions/customer/1")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subscriptionId", is("SUB-123456")));
    }

    @Test
    void getCustomerSubscriptions_shouldReturnActiveOnly_whenStatusProvided() throws Exception {
        List<Subscription> activeSubscriptions = Arrays.asList(testSubscription);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(subscriptionService.getActiveSubscriptionsByCustomer(testCustomer)).thenReturn(activeSubscriptions);

        mockMvc.perform(get("/subscriptions/customer/1?status=ACTIVE")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));
    }

    @Test
    void cancelSubscription_shouldReturnOk() throws Exception {
        PaymentResponse cancelResponse = PaymentResponse.builder()
                .transactionId("SUB-123456")
                .status("CANCELLED")
                .responseMessage("Subscription cancelled successfully")
                .build();

        when(subscriptionService.cancelSubscription(eq("SUB-123456"), anyString(), anyString()))
                .thenReturn(cancelResponse);

        mockMvc.perform(post("/subscriptions/SUB-123456/cancel?reason=Customer+request")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void updateSubscriptionAmount_shouldReturnOk() throws Exception {
        PaymentResponse updateResponse = PaymentResponse.builder()
                .transactionId("SUB-123456")
                .status("UPDATED")
                .amount(new BigDecimal("29.99"))
                .responseMessage("Subscription amount updated successfully")
                .build();

        when(subscriptionService.updateSubscriptionAmount(eq("SUB-123456"), eq(new BigDecimal("29.99")), anyString()))
                .thenReturn(updateResponse);

        SubscriptionUpdateRequest updateRequest = new SubscriptionUpdateRequest();
        updateRequest.setAmount(new BigDecimal("29.99"));

        mockMvc.perform(post("/subscriptions/SUB-123456/amount")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UPDATED")))
                .andExpect(jsonPath("$.amount", is(29.99)));
    }

    @Test
    void suspendSubscription_shouldReturnOk() throws Exception {
        PaymentResponse suspendResponse = PaymentResponse.builder()
                .transactionId("SUB-123456")
                .status("SUSPENDED")
                .responseMessage("Subscription suspended successfully")
                .build();

        when(subscriptionService.suspendSubscription(eq("SUB-123456"), anyString(), anyString()))
                .thenReturn(suspendResponse);

        mockMvc.perform(post("/subscriptions/SUB-123456/suspend?reason=Payment+issue")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")));
    }

    @Test
    void reactivateSubscription_shouldReturnOk() throws Exception {
        PaymentResponse reactivateResponse = PaymentResponse.builder()
                .transactionId("SUB-123456")
                .status("ACTIVE")
                .responseMessage("Subscription reactivated successfully")
                .build();

        when(subscriptionService.reactivateSubscription(eq("SUB-123456"), anyString()))
                .thenReturn(reactivateResponse);

        mockMvc.perform(post("/subscriptions/SUB-123456/reactivate")
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }
}
