# Payment Gateway Architecture

This document outlines the architectural design of the Payment Gateway integration with Authorize.Net, including flows, database schema, design decisions, and compliance considerations.

## Overview of Flows Implemented

### Payment Flow

1. **One-Time Payment Flow**:
   ```
   Client → Payment Request → Payment Gateway → Authorize.Net API → Response → Client
   ```

   - Client sends payment details (card info, amount, etc.)
   - Gateway validates input and checks for duplicate requests via idempotency key
   - Gateway sends request to Authorize.Net
   - Response is stored in database and returned to client
   - Transaction details are logged with correlation ID for tracing

2. **Auth-Capture Flow**:
   ```
   Client → Auth Request → Payment Gateway → Authorize.Net API → Auth Response → Client
   ...later...
   Client → Capture Request → Payment Gateway → Authorize.Net API → Capture Response → Client
   ```

   - Separation of authorization and capture allows for delayed fund capture
   - Useful for services/products that are delivered after payment authorization

3. **Refund Flow**:
   ```
   Client → Refund Request → Payment Gateway → Authorize.Net API → Refund Response → Client
   ```

   - Full or partial refunds are supported
   - Original transaction ID is required for refund processing
   - Idempotency ensures refunds aren't processed multiple times

4. **Webhook Processing Flow**:
   ```
   Authorize.Net → Webhook Event → Payment Gateway → Process Event → Update Transaction Status
   ```

   - Asynchronous notifications for payment events
   - Events are persisted before processing to prevent data loss
   - Failed webhook processing is retried with exponential backoff

### Subscription/Recurring Payment Flow

1. **Subscription Creation**:
   ```
   Client → Subscription Request → Payment Gateway → Authorize.Net API → Create Subscription → Response → Client
   ```

   - Initial setup of payment method and billing schedule
   - Creation of subscription record in Authorize.Net and local database

2. **Subscription Management**:
   ```
   Client → Update/Cancel Request → Payment Gateway → Authorize.Net API → Update Subscription → Response → Client
   ```

   - Operations: cancel, update amount, suspend, reactivate
   - Each operation updates both remote and local subscription status

3. **Subscription Webhook Processing**:
   ```
   Authorize.Net → Subscription Event → Payment Gateway → Process Event → Update Subscription Status
   ```

   - Automatic handling of subscription lifecycle events
   - Status updates: created, updated, suspended, terminated, expiring, expired

## Database Schema & Entity Relationships

### Core Entities

1. **Transaction**
   - Primary entity storing payment transaction details
   - Relationships: Many transactions can belong to one customer
   - Fields:
     - `id`: Primary key (UUID)
     - `transactionId`: External transaction ID from Authorize.Net
     - `customerId`: Reference to customer
     - `amount`: Transaction amount
     - `currencyCode`: Currency code (default USD)
     - `status`: Transaction status (APPROVED, DECLINED, ERROR)
     - `type`: Transaction type (PAYMENT, AUTH, CAPTURE, REFUND, VOID)
     - `cardType`: Type of card used
     - `last4Digits`: Last 4 digits of card (for display purposes)
     - `authCode`: Authorization code from processor
     - `responseCode`: Response code from processor
     - `responseMessage`: Human-readable response message
     - `originalTransactionId`: For refunds/voids, reference to original transaction
     - `createdAt`, `updatedAt`: Timestamps
     - `correlationId`: For request tracing
     - `idempotencyKey`: To prevent duplicate processing

2. **Customer**
   - Stores customer information
   - Relationships: One customer has many transactions and subscriptions
   - Fields:
     - `id`: Primary key (UUID)
     - `externalId`: External customer ID
     - `email`: Customer email
     - `firstName`, `lastName`: Customer name
     - `createdAt`, `updatedAt`: Timestamps

3. **Subscription**
   - Stores subscription/recurring payment details
   - Relationships: Many subscriptions can belong to one customer
   - Fields:
     - `id`: Primary key (UUID)
     - `subscriptionId`: External subscription ID from Authorize.Net
     - `customerId`: Reference to customer
     - `status`: Subscription status (ACTIVE, SUSPENDED, CANCELLED, EXPIRED)
     - `name`: Subscription name/plan
     - `amount`: Billing amount
     - `currencyCode`: Currency code
     - `intervalLength`: Length of billing interval
     - `intervalUnit`: Unit of billing interval (DAY, MONTH, YEAR)
     - `startDate`: Subscription start date
     - `nextBillingDate`: Next scheduled billing date
     - `totalOccurrences`: Total number of billings
     - `completedOccurrences`: Number of completed billings
     - `createdAt`, `updatedAt`: Timestamps

4. **WebhookEvent**
   - Stores incoming webhook events from Authorize.Net
   - Fields:
     - `id`: Primary key (UUID)
     - `eventId`: External event ID
     - `eventType`: Type of webhook event
     - `payload`: JSON payload of the event
     - `processed`: Whether event has been processed
     - `processingAttempts`: Number of processing attempts
     - `lastError`: Last error message if processing failed
     - `receivedAt`, `processedAt`: Timestamps

### Entity Relationship Diagram (ERD)

```
Customer (1) --- (N) Transaction
Customer (1) --- (N) Subscription
WebhookEvent (standalone)
```

## Design Trade-offs

### Synchronous vs Asynchronous Processing

**Payment Processing**: Synchronous approach was chosen for direct payment operations.
- **Pros**: Immediate response to user, simpler error handling
- **Cons**: Longer wait times for users during processing
- **Rationale**: Payment confirmation is critical for user experience and requires immediate feedback

**Webhook Processing**: Asynchronous approach for handling notifications.
- **Pros**: Non-blocking, can handle high volumes of events
- **Cons**: Complexity in ensuring event processing
- **Rationale**: Webhooks arrive unpredictably and shouldn't block main payment flows

### Retry Strategies

1. **Payment API Calls**:
   - Immediate retry once for transient errors
   - Exponential backoff for subsequent retries
   - Maximum of 3 retries before failing
   - Circuit breaker pattern to prevent cascading failures

2. **Webhook Processing**:
   - Events are persisted before processing
   - Failed processing is retried with exponential backoff
   - Maximum of 5 retries with increasing intervals
   - Dead-letter queue for events that fail repeatedly

### Queueing

1. **Webhook Event Queue**:
   - Events are placed in a processing queue
   - Ensures events are processed in order
   - Allows for rate limiting of API calls to Authorize.Net
   - Provides buffer during high volume periods

2. **Transaction Reporting Queue**:
   - Asynchronous generation of reports and analytics
   - Prevents reporting queries from affecting payment processing performance

### Data Persistence

1. **Transaction Data**:
   - All transaction data is persisted immediately
   - Transaction records are never deleted, only marked with different statuses
   - Ensures complete audit trail for all payment operations

2. **Sensitive Data**:
   - Card data is never stored in our database
   - Only last 4 digits and card type are stored for reference
   - All sensitive data handling is delegated to Authorize.Net

## Compliance Considerations

### PCI DSS Compliance

1. **Card Data Handling**:
   - Card data is never logged or stored in our system
   - Data is transmitted directly to Authorize.Net
   - Reduces PCI DSS scope by leveraging Authorize.Net's compliance

2. **Data Security**:
   - All API communications use TLS 1.2+
   - Sensitive configuration is stored in environment variables, not in code
   - Secrets management for API credentials

### GDPR Compliance

1. **Customer Data**:
   - Only necessary customer data is collected
   - Data retention policies are implemented
   - Data deletion capabilities for customer requests

2. **Consent Management**:
   - Clear documentation of data usage
   - Mechanisms for withdrawing consent
   - Data portability through export APIs

### Audit and Logging

1. **Transaction Logging**:
   - Complete audit trail for all payment operations
   - Correlation IDs for tracing requests across systems
   - Structured logging with sensitive data redaction

2. **Access Control**:
   - Role-based access control for admin functions
   - Detailed audit logs for all admin actions
   - Regular access reviews

## System Resilience

1. **Database Redundancy**:
   - Primary-replica setup for database
   - Automated failover

2. **Circuit Breakers**:
   - Prevent cascading failures when Authorize.Net is unavailable
   - Graceful degradation of non-critical features

3. **Monitoring and Alerts**:
   - Real-time monitoring of payment success rates
   - Alerts for unusual failure patterns
   - SLA monitoring for Authorize.Net API

## Future Architecture Considerations

1. **Microservices Split**:
   - Separating payment processing, subscriptions, and reporting into distinct services
   - Event-driven communication between services

2. **Multi-Provider Support**:
   - Abstraction layer to support multiple payment processors
   - Strategy pattern for processor-specific implementations

3. **Enhanced Analytics**:
   - Real-time payment analytics
   - Fraud detection based on payment patterns

4. **Global Expansion**:
   - Multi-currency support
   - Regional payment method additions
