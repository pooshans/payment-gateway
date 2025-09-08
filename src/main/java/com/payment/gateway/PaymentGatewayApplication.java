package com.payment.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.UUID;

/**
 * Main application class for the Payment Gateway service.
 * This application provides integration with Authorize.NET payment processing
 * API,
 * allowing merchants to process payments, refunds, and manage subscriptions.
 * It includes features for distributed tracing, correlation IDs, webhook
 * handling,
 * and comprehensive observability.
 * 
 * @author Payment Gateway Team
 */
@SpringBootApplication
public class PaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewayApplication.class, args);
    }

    /**
     * Bean for generating correlation IDs
     */
    @Bean
    public String correlationIdGenerator() {
        return UUID.randomUUID().toString();
    }
}
