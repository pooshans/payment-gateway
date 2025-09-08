package com.payment.gateway.api;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerCancelTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    public void testVoidPayment() throws Exception {
        String transactionId = "test-transaction-id";

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("void-transaction-id")
                .originalTransactionId(transactionId)
                .status("VOIDED")
                .responseCode("1")
                .responseMessage("Transaction voided successfully")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .build();

        // Mock the service
        when(paymentService.voidPayment(eq(transactionId), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/cancel/{transactionId}", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("void-transaction-id"))
                .andExpect(jsonPath("$.originalTransactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }
}
