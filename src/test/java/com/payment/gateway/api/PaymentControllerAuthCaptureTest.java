package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.AuthorizationRequest;
import com.payment.gateway.dto.CaptureRequest;
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
public class PaymentControllerAuthCaptureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testAuthorizePayment() throws Exception {
        // Create an authorization request
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
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

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("test-auth-id")
                .status("APPROVED")
                .responseCode("1")
                .responseMessage("Authorization approved")
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .cardType("Visa")
                .last4Digits("1111")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .isAuthorized(true)
                .isCaptured(false)
                .build();

        // Mock the service
        when(paymentService.authorizePayment(any(AuthorizationRequest.class), any(String.class), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("test-auth-id"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.isAuthorized").value(true))
                .andExpect(jsonPath("$.isCaptured").value(false));
    }

    @Test
    public void testCapturePayment() throws Exception {
        // Create a capture request
        CaptureRequest captureRequest = CaptureRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .description("Capturing authorized payment")
                .build();

        String transactionId = "test-auth-id";

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("test-capture-id")
                .originalTransactionId(transactionId)
                .status("APPROVED")
                .responseCode("1")
                .responseMessage("Capture approved")
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .isAuthorized(true)
                .isCaptured(true)
                .build();

        // Mock the service
        when(paymentService.capturePayment(any(String.class), any(CaptureRequest.class), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/capture/{transactionId}", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(captureRequest))
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("test-capture-id"))
                .andExpect(jsonPath("$.originalTransactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.isAuthorized").value(true))
                .andExpect(jsonPath("$.isCaptured").value(true));
    }
}
