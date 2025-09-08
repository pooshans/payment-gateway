package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.RecurringPaymentRequest;
import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerRecurringTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateRecurringPayment() throws Exception {
        // Create a recurring payment request
        RecurringPaymentRequest recurringRequest = RecurringPaymentRequest.builder()
                .cardNumber("4111111111111111")
                .expirationDate("12/2025")
                .cardSecurityCode("123")
                .cardholderName("Test User")
                .amount(new BigDecimal("19.99"))
                .currencyCode("USD")
                .subscriptionName("Monthly Subscription")
                .intervalLength(1)
                .intervalUnit("months")
                .totalOccurrences(12)
                .startDate("2023-06-01")
                .firstName("Test")
                .lastName("User")
                .address("123 Test St")
                .city("Test City")
                .state("CA")
                .zip("12345")
                .country("US")
                .build();

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("initial-transaction-id")
                .subscriptionId("subscription-123")
                .status("ACTIVE")
                .responseCode("1")
                .responseMessage("Recurring payment created successfully")
                .amount(new BigDecimal("19.99"))
                .currencyCode("USD")
                .cardType("Visa")
                .last4Digits("1111")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .build();

        // Mock the service
        when(paymentService.createRecurringPayment(any(RecurringPaymentRequest.class), any(String.class),
                any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/recurring")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(recurringRequest))
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("initial-transaction-id"))
                .andExpect(jsonPath("$.subscriptionId").value("subscription-123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amount").value(19.99));
    }
}
