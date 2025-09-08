# Project Structure Documentation

This document outlines the structure of the Payment Gateway application, explaining the organization of packages, classes, and their responsibilities.

## Root Structure

```
payment-gateway/
├── src/                      # Source code
│   ├── main/                 # Main application code
│   │   ├── java/             # Java source files
│   │   └── resources/        # Configuration and static resources
│   └── test/                 # Test code
├── docker-compose.yml        # Docker Compose configuration
├── Dockerfile                # Docker build instructions
├── pom.xml                   # Maven project configuration
├── prometheus.yml           # Prometheus configuration
└── README.md                # Project documentation
```

## Package Organization

The application follows a standard layered architecture with the following package structure:

### `com.payment.gateway`

Root package containing the main application class.

- `PaymentGatewayApplication.java` - Spring Boot entry point with application configuration.

### `com.payment.gateway.api`

Contains REST controllers that handle HTTP requests.

- `PaymentController.java` - Handles all payment-related API endpoints.

### `com.payment.gateway.config`

Contains configuration classes for the application.

- `SecurityConfig.java` (to be implemented) - Spring Security configuration.
- `AuthorizeNetConfig.java` (to be implemented) - Authorize.Net client configuration.

### `com.payment.gateway.domain`

Contains domain entities that represent the business objects.

- `Transaction.java` (to be implemented) - Represents a payment transaction.
- `PaymentMethod.java` (to be implemented) - Represents a payment method.
- `RecurringPayment.java` (to be implemented) - Represents a recurring payment subscription.

### `com.payment.gateway.dto`

Data Transfer Objects for API requests and responses.

- `PaymentRequest.java` - DTO for payment request data.
- `PaymentResponse.java` - DTO for payment response data.
- `AuthorizationRequest.java` - DTO for authorization request data.
- `CaptureRequest.java` - DTO for capture request data.
- `RefundRequest.java` - DTO for refund request data.
- `RecurringPaymentRequest.java` - DTO for recurring payment request data.

### `com.payment.gateway.exception`

Custom exceptions and exception handling.

- `GlobalExceptionHandler.java` - Global exception handler for the API.
- `PaymentProcessingException.java` - Exception for payment processing errors.
- `ResourceNotFoundException.java` - Exception for resource not found errors.
- `ErrorResponse.java` - Standard error response structure.

### `com.payment.gateway.filter`

HTTP filters for cross-cutting concerns.

- `CorrelationIdFilter.java` - Manages correlation IDs for request tracing.

### `com.payment.gateway.repository`

Data access layer interfaces.

- `TransactionRepository.java` (to be implemented) - Repository for transaction entities.
- `PaymentMethodRepository.java` (to be implemented) - Repository for payment method entities.

### `com.payment.gateway.service`

Business logic layer with service interfaces and implementations.

- `PaymentService.java` - Interface defining payment operations.
- `impl/AuthorizeNetPaymentService.java` - Implementation of payment service using Authorize.Net.

### `com.payment.gateway.util`

Utility classes and helper methods.

- `IdempotencyKeyGenerator.java` (to be implemented) - Utility for generating idempotency keys.
- `CreditCardUtils.java` (to be implemented) - Utilities for credit card validation and masking.

## Resources

### `src/main/resources`

- `application.yml` - Application configuration including database, API keys, etc.
- `db/migration` (to be implemented) - Database migration scripts if using Flyway/Liquibase.

## Testing Structure

### `src/test/java`

Mirrors the main package structure for unit and integration tests.

- `com.payment.gateway.api` - Controller tests.
- `com.payment.gateway.service` - Service layer tests.
- `com.payment.gateway.repository` - Repository tests.

## Docker Configuration

- `Dockerfile` - Multistage build for the application.
- `docker-compose.yml` - Services definition including the application, database, and monitoring tools.
- `prometheus.yml` - Prometheus configuration for metrics collection.
