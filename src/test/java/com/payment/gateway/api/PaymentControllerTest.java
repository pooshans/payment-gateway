package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.RefundRequest;
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
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testRefundPayment() throws Exception {
        // Create a refund request
        RefundRequest refundRequest = RefundRequest.builder()
                .amount(new BigDecimal("50.00"))
                .reason("Customer return")
                .currencyCode("USD")
                .build();

        String transactionId = "test-transaction-id";

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("refund-transaction-id")
                .originalTransactionId(transactionId)
                .status("REFUNDED")
                .responseCode("1")
                .responseMessage("Refund processed successfully")
                .amount(new BigDecimal("50.00"))
                .currencyCode("USD")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .build();

        // Mock the service
        when(paymentService.refundPayment(any(RefundRequest.class), any(String.class), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/refund/{transactionId}", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest))
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("refund-transaction-id"))
                .andExpect(jsonPath("$.originalTransactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    public void testPartialRefundPayment() throws Exception {
        // Create a partial refund request
        RefundRequest refundRequest = RefundRequest.builder()
                .amount(new BigDecimal("25.00")) // Partial amount
                .reason("Partial refund")
                .currencyCode("USD")
                .build();

        String transactionId = "test-transaction-id";

        // Create expected response
        PaymentResponse mockResponse = PaymentResponse.builder()
                .transactionId("partial-refund-transaction-id")
                .originalTransactionId(transactionId)
                .status("REFUNDED")
                .responseCode("1")
                .responseMessage("Partial refund processed successfully")
                .amount(new BigDecimal("25.00"))
                .currencyCode("USD")
                .transactionDate(LocalDateTime.now())
                .correlationId("test-correlation-id")
                .build();

        // Mock the service
        when(paymentService.refundPayment(any(RefundRequest.class), any(String.class), any(String.class)))
                .thenReturn(mockResponse);

        // Perform the test
        mockMvc.perform(post("/payments/partial-refund/{transactionId}", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest))
                .header("X-Correlation-ID", "test-correlation-id")
                .header("X-Idempotency-Key", "test-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("partial-refund-transaction-id"))
                .andExpect(jsonPath("$.originalTransactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }
}
