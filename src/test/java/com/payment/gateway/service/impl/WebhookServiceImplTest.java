package com.payment.gateway.service.impl;

import com.payment.gateway.dto.AuthorizeNetWebhookRequest;
import com.payment.gateway.dto.WebhookResponse;
import com.payment.gateway.entity.WebhookEvent;
import com.payment.gateway.repository.WebhookEventRepository;
import com.payment.gateway.service.MetricsService;
import com.payment.gateway.service.WebhookProcessor;
import com.payment.gateway.util.CorrelationIdUtils;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceImplTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookProcessor webhookProcessor;

    @Mock
    private MetricsService metricsService;

    @Mock
    private CorrelationIdUtils correlationIdUtils;

    @Mock
    private Timer.Sample timerSample;

    private WebhookServiceImpl webhookService;

    @BeforeEach
    public void setUp() {
        webhookService = new WebhookServiceImpl(webhookEventRepository, webhookProcessor, metricsService,
                correlationIdUtils);

        // Set the signature key
        ReflectionTestUtils.setField(webhookService, "authorizeNetSignatureKey", "test-signature-key");

        // Mock the timer
        when(metricsService.startTimer()).thenReturn(timerSample);
    }

    @Test
    public void testProcessAuthorizeNetWebhook_Success() {
        // Arrange
        AuthorizeNetWebhookRequest request = createSampleWebhookRequest();
        String rawPayload = "{\"notificationId\":\"test-notification-id\",\"eventType\":\"net.authorize.payment.authorization.created\"}";
        String correlationId = "test-correlation-id";

        // Mock repository to indicate this webhook hasn't been processed before
        when(webhookEventRepository.findByEventId(anyString())).thenReturn(Optional.empty());

        // Mock webhook event save
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        when(webhookEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WebhookResponse response = webhookService.processAuthorizeNetWebhook(request, rawPayload, correlationId);

        // Assert
        assertNotNull(response);
        assertEquals("accepted", response.getStatus());
        assertEquals("test-notification-id", response.getEventId());

        // Verify correlationId was set
        verify(correlationIdUtils).setCorrelationId(correlationId);

        // Verify metrics were recorded
        verify(metricsService).recordWebhookReceived(request.getEventType());
        verify(metricsService).stopTimer(eq(timerSample), eq("payment.gateway.webhook.processing"),
                eq("eventType"), eq(request.getEventType()),
                eq("result"), eq("accepted"));

        // Verify webhook was saved
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent);
        assertEquals("test-notification-id", savedEvent.getEventId());
        assertEquals("net.authorize.payment.authorization.created", savedEvent.getEventType());
        assertEquals("AUTHORIZE_NET", savedEvent.getEventSource());
        assertEquals(rawPayload, savedEvent.getPayload());
        assertEquals(correlationId, savedEvent.getCorrelationId());

        // Verify async processing was scheduled
        verify(webhookProcessor, never()).processWebhookEvent(any()); // Direct call should never happen
    }

    @Test
    public void testProcessAuthorizeNetWebhook_DuplicateWebhook() {
        // Arrange
        AuthorizeNetWebhookRequest request = createSampleWebhookRequest();
        String rawPayload = "{\"notificationId\":\"test-notification-id\",\"eventType\":\"net.authorize.payment.authorization.created\"}";
        String correlationId = "test-correlation-id";

        // Mock repository to indicate this webhook has been processed before
        WebhookEvent existingEvent = WebhookEvent.builder()
                .eventId("test-notification-id")
                .eventType("net.authorize.payment.authorization.created")
                .build();
        when(webhookEventRepository.findByEventId("test-notification-id")).thenReturn(Optional.of(existingEvent));

        // Act
        WebhookResponse response = webhookService.processAuthorizeNetWebhook(request, rawPayload, correlationId);

        // Assert
        assertNotNull(response);
        assertEquals("duplicate", response.getStatus());
        assertEquals("test-notification-id", response.getEventId());

        // Verify metrics
        verify(metricsService).stopTimer(eq(timerSample), eq("payment.gateway.webhook.processing"),
                eq("eventType"), eq(request.getEventType()),
                eq("result"), eq("duplicate"));

        // Verify no save or processing happens
        verify(webhookEventRepository, never()).save(any());
        verify(webhookProcessor, never()).processWebhookEvent(any());
    }

    @Test
    public void testProcessAuthorizeNetWebhook_InvalidSignature() {
        // Arrange - create a service with a real signature key for testing
        WebhookServiceImpl serviceWithRealSigCheck = new WebhookServiceImpl(
                webhookEventRepository, webhookProcessor, metricsService, correlationIdUtils);
        ReflectionTestUtils.setField(serviceWithRealSigCheck, "authorizeNetSignatureKey", "real-signature-key");

        when(metricsService.startTimer()).thenReturn(timerSample);

        AuthorizeNetWebhookRequest request = createSampleWebhookRequest();
        request.setSignature("invalid-signature");
        String rawPayload = "{\"notificationId\":\"test-notification-id\",\"eventType\":\"net.authorize.payment.authorization.created\"}";
        String correlationId = "test-correlation-id";

        // Mock repository
        when(webhookEventRepository.findByEventId(anyString())).thenReturn(Optional.empty());
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WebhookResponse response = serviceWithRealSigCheck.processAuthorizeNetWebhook(request, rawPayload,
                correlationId);

        // Assert
        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertEquals("Invalid signature", response.getMessage());

        // Verify metrics
        verify(metricsService).stopTimer(eq(timerSample), eq("payment.gateway.webhook.processing"),
                eq("eventType"), eq(request.getEventType()),
                eq("result"), eq("invalid_signature"));
    }

    @Test
    public void testProcessAuthorizeNetWebhook_WithRelatedIds() {
        // Arrange
        AuthorizeNetWebhookRequest request = createSampleWebhookRequest();

        // Add related IDs to the payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "payment-123");

        Map<String, Object> subscription = new HashMap<>();
        subscription.put("id", "subscription-456");
        payload.put("subscription", subscription);

        Map<String, Object> customer = new HashMap<>();
        customer.put("id", "789");
        payload.put("customer", customer);

        request.setPayload(payload);

        String rawPayload = "{\"notificationId\":\"test-notification-id\",\"eventType\":\"net.authorize.payment.authorization.created\"}";
        String correlationId = "test-correlation-id";

        // Mock repository
        when(webhookEventRepository.findByEventId(anyString())).thenReturn(Optional.empty());

        // Mock webhook event save
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        when(webhookEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WebhookResponse response = webhookService.processAuthorizeNetWebhook(request, rawPayload, correlationId);

        // Assert
        assertNotNull(response);
        assertEquals("accepted", response.getStatus());

        // Verify related IDs were extracted
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertEquals("payment-123", savedEvent.getRelatedPaymentId());
        assertEquals("subscription-456", savedEvent.getRelatedSubscriptionId());
        assertEquals(789L, savedEvent.getRelatedCustomerId());
    }

    @Test
    public void testRetryFailedEvents_NoFailedEvents() {
        // Arrange
        when(webhookEventRepository.findByProcessedFalseOrderByReceivedAtAsc())
                .thenReturn(java.util.Collections.emptyList());

        // Act
        webhookService.retryFailedEvents();

        // Assert - verify no processing happens
        verify(webhookProcessor, never()).processWebhookEvent(any());
    }

    @Test
    public void testRetryFailedEvents_WithFailedEvents() {
        // Arrange
        WebhookEvent failedEvent1 = WebhookEvent.builder()
                .id(1L)
                .eventId("failed-1")
                .eventType("net.authorize.payment.authorization.created")
                .processed(false)
                .processingAttempts(1)
                .build();

        WebhookEvent failedEvent2 = WebhookEvent.builder()
                .id(2L)
                .eventId("failed-2")
                .eventType("net.authorize.payment.authcapture.created")
                .processed(false)
                .processingAttempts(2)
                .build();

        when(webhookEventRepository.findByProcessedFalseOrderByReceivedAtAsc())
                .thenReturn(java.util.Arrays.asList(failedEvent1, failedEvent2));

        // Mock successful processing of first event
        when(webhookProcessor.processWebhookEvent(failedEvent1)).thenReturn(true);

        // Mock failed processing of second event
        when(webhookProcessor.processWebhookEvent(failedEvent2)).thenReturn(false);

        // Act
        webhookService.retryFailedEvents();

        // Assert
        verify(webhookEventRepository).save(failedEvent1);
        verify(webhookEventRepository).save(failedEvent2);

        // First event should be marked as processed
        assertTrue(failedEvent1.getProcessed());

        // Second event should still be unprocessed with incremented attempt counter
        assertFalse(failedEvent2.getProcessed());
        assertEquals(3, failedEvent2.getProcessingAttempts());
    }

    @Test
    public void testProcessWebhookEventAsync_Success() throws Exception {
        // Arrange
        WebhookEvent event = WebhookEvent.builder()
                .id(1L)
                .eventId("event-id")
                .eventType("net.authorize.payment.authorization.created")
                .processed(false)
                .build();

        // Mock correlation ID utils
        doNothing().when(correlationIdUtils).setCorrelationId(anyString());

        // Mock successful processing
        when(webhookProcessor.processWebhookEvent(event)).thenReturn(true);

        // Use reflection to call the private method
        java.lang.reflect.Method method = WebhookServiceImpl.class.getDeclaredMethod(
                "processWebhookEventAsync", WebhookEvent.class);
        method.setAccessible(true);

        // Act
        method.invoke(webhookService, event);

        // Assert
        verify(webhookProcessor).processWebhookEvent(event);
        verify(webhookEventRepository).save(event);
        assertTrue(event.getProcessed());
        assertNotNull(event.getProcessedAt());
        verify(metricsService).recordWebhookProcessedSuccess(event.getEventType());
    }

    @Test
    public void testProcessWebhookEventAsync_Failure() throws Exception {
        // Arrange
        WebhookEvent event = WebhookEvent.builder()
                .id(1L)
                .eventId("event-id")
                .eventType("net.authorize.payment.authorization.created")
                .processed(false)
                .build();

        // Mock correlation ID utils
        doNothing().when(correlationIdUtils).setCorrelationId(anyString());

        // Mock failed processing
        when(webhookProcessor.processWebhookEvent(event)).thenReturn(false);

        // Use reflection to call the private method
        java.lang.reflect.Method method = WebhookServiceImpl.class.getDeclaredMethod(
                "processWebhookEventAsync", WebhookEvent.class);
        method.setAccessible(true);

        // Act
        method.invoke(webhookService, event);

        // Assert
        verify(webhookProcessor).processWebhookEvent(event);
        verify(webhookEventRepository).save(event);
        assertFalse(event.getProcessed());
        assertNull(event.getProcessedAt());
        assertEquals(1, event.getProcessingAttempts());
        verify(metricsService).recordWebhookProcessedFailure(event.getEventType());
    }

    private AuthorizeNetWebhookRequest createSampleWebhookRequest() {
        AuthorizeNetWebhookRequest request = new AuthorizeNetWebhookRequest();
        request.setNotificationId("test-notification-id");
        request.setEventType("net.authorize.payment.authorization.created");
        request.setEventDate("2025-09-09T12:00:00.000Z");
        request.setWebhookId("webhook-123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", "transaction-123");
        payload.put("amount", "100.00");
        request.setPayload(payload);

        return request;
    }
}
