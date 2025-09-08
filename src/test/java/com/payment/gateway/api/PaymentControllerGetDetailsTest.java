package com.payment.gateway.api;

import com.payment.gateway.dto.PaymentResponse;
import com.payment.gateway.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerGetDetailsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    public void testGetPaymentDetails() throws Exception {
        String transactionId = "test-transaction-id";

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId(transactionId)
                .status("SETTLED")
                .responseCode("1")
                .responseMessage("Transaction settled")
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .cardType("Visa")
                .last4Digits("1111")
                .transactionDate(LocalDateTime.now().minusHours(24)) // Transaction from yesterday
                .correlationId("test-correlation-id")
                .isAuthorized(true)
                .isCaptured(true)
                .build();

        // Mock the service
        when(paymentService.getPaymentDetails(eq(transactionId), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(get("/payments/{transactionId}", transactionId)
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.cardType").value("Visa"))
                .andExpect(jsonPath("$.last4Digits").value("1111"));
    }
}
