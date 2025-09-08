package com.payment.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.dto.AuthorizeNetWebhookRequest;
import com.payment.gateway.dto.WebhookResponse;
import com.payment.gateway.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookController webhookController;

    private MockMvc mockMvc;
    private ObjectMapper realObjectMapper;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build();
        realObjectMapper = new ObjectMapper();
    }

    @Test
    public void testHealthCheck() throws Exception {
        mockMvc.perform(get("/webhooks/authorizenet/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook endpoint is healthy"));
    }

    @Test
    public void testHandleAuthorizeNetWebhook_Success() throws Exception {
        // Create a sample webhook request
        AuthorizeNetWebhookRequest webhookRequest = new AuthorizeNetWebhookRequest();
        webhookRequest.setNotificationId("test-notification-id");
        webhookRequest.setEventType("net.authorize.payment.authcapture.created");

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "test-payment-id");
        webhookRequest.setPayload(payload);

        String rawPayload = realObjectMapper.writeValueAsString(webhookRequest);

        // Mock the ObjectMapper to return our webhook request
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(webhookRequest);

        // Mock the webhook service to return a success response
        WebhookResponse successResponse = WebhookResponse.accepted("test-notification-id");
        when(webhookService.processAuthorizeNetWebhook(any(), anyString(), anyString())).thenReturn(successResponse);

        // Perform the POST request
        mockMvc.perform(post("/webhooks/authorizenet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawPayload)
                .header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(content().json(realObjectMapper.writeValueAsString(successResponse)));
    }

    @Test
    public void testHandleAuthorizeNetWebhook_InvalidSignature() throws Exception {
        // Create a sample webhook request
        AuthorizeNetWebhookRequest webhookRequest = new AuthorizeNetWebhookRequest();
        webhookRequest.setNotificationId("test-notification-id");
        webhookRequest.setEventType("net.authorize.payment.authcapture.created");

        String rawPayload = realObjectMapper.writeValueAsString(webhookRequest);

        // Mock the ObjectMapper to return our webhook request
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(webhookRequest);

        // Mock the webhook service to return an invalid signature response
        WebhookResponse invalidSignatureResponse = WebhookResponse.invalidSignature();
        when(webhookService.processAuthorizeNetWebhook(any(), anyString(), anyString()))
                .thenReturn(invalidSignatureResponse);

        // Perform the POST request
        mockMvc.perform(post("/webhooks/authorizenet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(realObjectMapper.writeValueAsString(invalidSignatureResponse)));
    }

    @Test
    public void testHandleAuthorizeNetWebhook_InvalidPayload() throws Exception {
        // Invalid JSON payload
        String invalidPayload = "{invalid-json:}";

        // Mock the ObjectMapper to throw an exception
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Invalid JSON"));

        // Perform the POST request with invalid payload
        mockMvc.perform(post("/webhooks/authorizenet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }
}
