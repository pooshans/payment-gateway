package com.payment.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_methods")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String type; // CREDIT_CARD, BANK_ACCOUNT, etc.

    @Column(nullable = true)
    private String paymentProfileId; // Authorize.Net payment profile ID

    // Credit Card Details
    @Column(length = 4)
    private String last4Digits;

    @Column
    private String cardType; // Visa, Mastercard, etc.

    @Column
    private String expirationMonth;

    @Column
    private String expirationYear;

    @Column
    private String cardholderName;

    // For Bank Account
    @Column
    private String accountType; // Checking, Savings

    @Column
    private String bankName;

    @Column
    private String routingNumber;

    @Column
    private Boolean isDefault;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isDefault == null) {
            this.isDefault = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
