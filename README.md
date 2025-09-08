# Payment Gateway Integration with Authorize.Net

This project implements a robust payment gateway backend application that integrates with the Authorize.Net Sandbox API. The application provides a comprehensive set of payment processing capabilities including purchases, authorizations, captures, cancellations, refunds, and recurring billing.

## Features

- **Complete Payment Processing**: Support for one-time payments, authorize+capture flow, voids, and refunds
- **Recurring Billing**: Create and manage subscription-based recurring payments
- **Webhook Support**: Receive and process payment notifications
- **Idempotency**: Prevent duplicate payment processing
- **Distributed Tracing**: Track requests across services for better monitoring and debugging
- **Correlation IDs**: All requests include correlation IDs for tracking and logging
- **High Test Coverage**: Unit test coverage ≥80%

## Tech Stack

- Java 17 with Spring Boot 3.2.4
- Spring Data JPA for database operations
- Spring Security for authentication and authorization
- PostgreSQL for production and H2 for development
- Micrometer for metrics and distributed tracing
- Zipkin for trace visualization
- Prometheus for metrics collection
- Docker and Docker Compose for containerization
- JUnit and Mockito for testing
- JaCoCo for test coverage reporting

## Prerequisites

- Java 17 or later
- Maven 3.8+ (or use the included Maven wrapper)
- Docker and Docker Compose (for containerized deployment)
- Authorize.Net Sandbox account (for API credentials)

## Quick Start

### Local Development Setup

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/payment-gateway.git
   cd payment-gateway
   ```

2. Configure Authorize.Net API credentials:
   ```
   Edit src/main/resources/application.yml and update the authorize.api section with your sandbox credentials
   ```

3. Build and run the application:
   ```
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

4. The application will be available at http://localhost:8080/api

### Docker Deployment

1. Set up environment variables for sensitive data:
   ```
   export AUTHORIZE_API_LOGIN_ID=your_login_id
   export AUTHORIZE_API_TRANSACTION_KEY=your_transaction_key
   export JWT_SECRET=your_jwt_secret
   ```

2. Build and start the Docker containers:
   ```
   docker-compose up -d
   ```

3. The application and related services will be available at:
   - Payment Gateway API: http://localhost:8080/api
   - Zipkin (Tracing): http://localhost:9411
   - Prometheus (Metrics): http://localhost:9090
   - PostgreSQL: localhost:5432

## API Endpoints

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| POST | /api/payments | Process a one-time payment |
| POST | /api/payments/authorize | Authorize a payment |
| POST | /api/payments/capture/{transactionId} | Capture a previously authorized payment |
| POST | /api/payments/void/{transactionId} | Void a transaction |
| POST | /api/payments/refund | Refund a settled transaction |
| POST | /api/payments/recurring | Create a recurring payment subscription |
| GET  | /api/payments/{transactionId} | Get payment details |

## Testing

Run the tests with:
```
./mvnw test
```

Generate a test coverage report with:
```
./mvnw verify
```

The coverage report will be available in the `target/site/jacoco` directory.

## Documentation

Additional documentation is available in the following files:
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md): Details about the project structure
- [Architecture.md](Architecture.md): Information about the application architecture
- [OBSERVABILITY.md](OBSERVABILITY.md): Monitoring, logging, and tracing information
- [API-SPECIFICATION.yml](API-SPECIFICATION.yml): OpenAPI specification for the API
- [TESTING_STRATEGY.md](TESTING_STRATEGY.md): Details about the testing approach

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
