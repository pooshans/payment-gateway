package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.PaymentRequest;
import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testProcessPayment() throws Exception {
        // Create a payment request
        PaymentRequest request = PaymentRequest.builder()
                .cardNumber("4111111111111111")
                .expirationDate("12/2025")
                .cardSecurityCode("123")
                .cardholderName("Test User")
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .firstName("Test")
                .lastName("User")
                .address("123 Test St")
                .city("Test City")
                .state("CA")
                .zip("12345")
                .country("US")
                .build();

        // Mock the service response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("test-transaction-id")
                .authCode("test-auth-code")
                .status("APPROVED")
                .responseCode("1")
                .responseMessage("Transaction approved")
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .cardType("Visa")
                .last4Digits("1111")
                .transactionDate(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .isAuthorized(true)
                .isCaptured(true)
                .build();

        when(paymentService.processPayment(any(PaymentRequest.class), anyString(), anyString()))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("test-transaction-id"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.responseCode").value("1"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"));
    }
}
