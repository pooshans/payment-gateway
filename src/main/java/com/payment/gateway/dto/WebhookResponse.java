package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private String status;
    private String message;
    private String eventId;

    public static WebhookResponse success(String eventId) {
        return WebhookResponse.builder()
                .status("success")
                .message("Webhook received and processed successfully")
                .eventId(eventId)
                .build();
    }

    public static WebhookResponse accepted(String eventId) {
        return WebhookResponse.builder()
                .status("accepted")
                .message("Webhook received and queued for processing")
                .eventId(eventId)
                .build();
    }

    public static WebhookResponse duplicate(String eventId) {
        return WebhookResponse.builder()
                .status("duplicate")
                .message("Webhook with this event ID has already been processed")
                .eventId(eventId)
                .build();
    }

    public static WebhookResponse error(String message) {
        return WebhookResponse.builder()
                .status("error")
                .message(message)
                .build();
    }

    public static WebhookResponse invalidSignature() {
        return WebhookResponse.builder()
                .status("error")
                .message("Invalid webhook signature")
                .build();
    }
}
