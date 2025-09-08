package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPaymentRequest {

    @NotBlank(message = "Card number is required")
    private String cardNumber;

    @NotBlank(message = "Expiration date is required")
    private String expirationDate; // Format: MM/YYYY

    @NotBlank(message = "Card security code is required")
    private String cardSecurityCode;

    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String currencyCode; // Default: USD

    @NotBlank(message = "Subscription name is required")
    private String subscriptionName;

    @NotNull(message = "Interval length is required")
    @Positive(message = "Interval length must be positive")
    private Integer intervalLength;

    @NotBlank(message = "Interval unit is required")
    private String intervalUnit; // days, months, etc.

    @NotNull(message = "Total occurrences is required")
    @Positive(message = "Total occurrences must be positive")
    private Integer totalOccurrences;

    @NotNull(message = "Start date is required")
    private String startDate; // Format: YYYY-MM-DD

    // Billing Address
    private String firstName;
    private String lastName;
    private String company;
    private String address;
    private String city;
    private String state;
    private String zip;
    private String country;
}
