package com.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeNetWebhookRequest {

    @JsonProperty("notificationId")
    private String notificationId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventDate")
    private String eventDate;

    @JsonProperty("webhookId")
    private String webhookId;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    // Signature information that Authorize.Net includes in webhook calls
    @JsonProperty("signature")
    private String signature;
}
