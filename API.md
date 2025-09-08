# Payment Gateway API Documentation

This document provides detailed information on the Payment Gateway API endpoints, including request/response formats and example curl commands.

## Table of Contents
- [Authentication](#authentication)
- [Payment Operations](#payment-operations)
  - [Process a Payment (Purchase)](#process-a-payment-purchase)
  - [Authorize a Payment](#authorize-a-payment)
  - [Capture an Authorized Payment](#capture-an-authorized-payment)
  - [Void/Cancel a Transaction](#voidcancel-a-transaction)
  - [Refund a Transaction](#refund-a-transaction)
  - [Partial Refund](#partial-refund)
  - [Get Transaction Details](#get-transaction-details)
- [Recurring Payments/Subscriptions](#recurring-paymentssubscriptions)
  - [Create a Subscription](#create-a-subscription)
  - [Get Subscription Details](#get-subscription-details)
  - [List Customer Subscriptions](#list-customer-subscriptions)
  - [Cancel a Subscription](#cancel-a-subscription)
  - [Update Subscription Amount](#update-subscription-amount)
  - [Suspend a Subscription](#suspend-a-subscription)
  - [Reactivate a Subscription](#reactivate-a-subscription)
- [Webhooks](#webhooks)
  - [Receive Authorize.Net Webhook](#receive-authorizenet-webhook)
  - [Webhook Health Check](#webhook-health-check)

## Authentication

All API endpoints require the following headers for authentication and idempotency:

- `X-Request-ID`: A unique identifier for the request to ensure idempotency
- `X-Correlation-ID`: A correlation ID for tracing requests through the system

## Payment Operations

### Process a Payment (Purchase)

Process a complete payment (combined authorization and capture).

**Endpoint:** `POST /payments/purchase`

**Request:**

```json
{
  "cardNumber": "4111111111111111",
  "expirationDate": "12/2025",
  "cardSecurityCode": "123",
  "cardholderName": "John Doe",
  "amount": 100.50,
  "currencyCode": "USD",
  "firstName": "John",
  "lastName": "Doe",
  "address": "123 Main St",
  "city": "Boston",
  "state": "MA",
  "zip": "02108",
  "country": "US",
  "orderDescription": "Test order",
  "orderNumber": "ORD-12345"
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/purchase" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12345" \
  -H "X-Correlation-ID: corr-id-12345" \
  -d '{
    "cardNumber": "4111111111111111",
    "expirationDate": "12/2025",
    "cardSecurityCode": "123",
    "cardholderName": "John Doe",
    "amount": 100.50,
    "currencyCode": "USD",
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US"
  }'
```

**Response (Success):**

```json
{
  "transactionId": "60157385847",
  "authCode": "ABCD1234",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:30:45",
  "correlationId": "corr-id-12345",
  "isAuthorized": true,
  "isCaptured": true
}
```

**Response (Error):**

```json
{
  "status": "ERROR",
  "responseCode": "2",
  "responseMessage": "This transaction has been declined.",
  "correlationId": "corr-id-12345"
}
```

### Authorize a Payment

Authorize a payment without capturing funds.

**Endpoint:** `POST /payments/authorize`

**Request:**

```json
{
  "cardNumber": "4111111111111111",
  "expirationDate": "12/2025",
  "cardSecurityCode": "123",
  "cardholderName": "John Doe",
  "amount": 100.50,
  "currencyCode": "USD",
  "firstName": "John",
  "lastName": "Doe",
  "address": "123 Main St",
  "city": "Boston",
  "state": "MA",
  "zip": "02108",
  "country": "US"
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/authorize" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12346" \
  -H "X-Correlation-ID: corr-id-12346" \
  -d '{
    "cardNumber": "4111111111111111",
    "expirationDate": "12/2025",
    "cardSecurityCode": "123",
    "cardholderName": "John Doe",
    "amount": 100.50,
    "currencyCode": "USD",
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US"
  }'
```

**Response:**

```json
{
  "transactionId": "60157385848",
  "authCode": "EFGH5678",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:35:12",
  "correlationId": "corr-id-12346",
  "isAuthorized": true,
  "isCaptured": false
}
```

### Capture an Authorized Payment

Capture funds for a previously authorized transaction.

**Endpoint:** `POST /payments/capture/{transactionId}`

**Request:**

```json
{
  "amount": 100.50,
  "currencyCode": "USD"
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/capture/60157385848" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12347" \
  -H "X-Correlation-ID: corr-id-12347" \
  -d '{
    "amount": 100.50,
    "currencyCode": "USD"
  }'
```

**Response:**

```json
{
  "transactionId": "60157385849",
  "originalTransactionId": "60157385848",
  "authCode": "EFGH5678",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:40:22",
  "correlationId": "corr-id-12347",
  "isAuthorized": true,
  "isCaptured": true
}
```

### Void/Cancel a Transaction

Cancel a previously authorized transaction.

**Endpoint:** `POST /payments/cancel/{transactionId}`

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/cancel/60157385848" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12348" \
  -H "X-Correlation-ID: corr-id-12348"
```

**Response:**

```json
{
  "transactionId": "60157385850",
  "originalTransactionId": "60157385848",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been voided.",
  "transactionDate": "2025-09-09T10:45:18",
  "correlationId": "corr-id-12348"
}
```

### Refund a Transaction

Refund a previously captured transaction.

**Endpoint:** `POST /payments/refund/{transactionId}`

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/refund/60157385849" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12349" \
  -H "X-Correlation-ID: corr-id-12349"
```

**Response:**

```json
{
  "transactionId": "60157385851",
  "originalTransactionId": "60157385849",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:50:33",
  "correlationId": "corr-id-12349"
}
```

### Partial Refund

Process a partial refund for a captured transaction.

**Endpoint:** `POST /payments/partial-refund/{transactionId}`

**Request:**

```json
{
  "amount": 50.25,
  "currencyCode": "USD"
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/partial-refund/60157385849" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12350" \
  -H "X-Correlation-ID: corr-id-12350" \
  -d '{
    "amount": 50.25,
    "currencyCode": "USD"
  }'
```

**Response:**

```json
{
  "transactionId": "60157385852",
  "originalTransactionId": "60157385849",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 50.25,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:55:42",
  "correlationId": "corr-id-12350"
}
```

### Get Transaction Details

Retrieve details of a specific transaction.

**Endpoint:** `GET /payments/{transactionId}`

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/payments/60157385849" \
  -H "X-Correlation-ID: corr-id-12351"
```

**Response:**

```json
{
  "transactionId": "60157385849",
  "authCode": "EFGH5678",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:40:22",
  "isAuthorized": true,
  "isCaptured": true
}
```

## Recurring Payments/Subscriptions

### Create a Subscription

Create a new subscription for a customer.

**Endpoint:** `POST /subscriptions/customer/{customerId}`

**Request:**

```json
{
  "paymentRequest": {
    "cardNumber": "4111111111111111",
    "expirationDate": "12/2025",
    "cardSecurityCode": "123",
    "cardholderName": "John Doe"
  },
  "name": "Premium Plan",
  "amount": 19.99,
  "currencyCode": "USD",
  "intervalLength": 1,
  "intervalUnit": "MONTH",
  "startDate": "2025-10-01",
  "totalOccurrences": 12,
  "trialOccurrences": 1
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/customer/cust-12345" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12352" \
  -H "X-Correlation-ID: corr-id-12352" \
  -d '{
    "paymentRequest": {
      "cardNumber": "4111111111111111",
      "expirationDate": "12/2025",
      "cardSecurityCode": "123",
      "cardholderName": "John Doe"
    },
    "name": "Premium Plan",
    "amount": 19.99,
    "currencyCode": "USD",
    "intervalLength": 1,
    "intervalUnit": "MONTH",
    "startDate": "2025-10-01",
    "totalOccurrences": 12,
    "trialOccurrences": 1
  }'
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "name": "Premium Plan",
  "status": "ACTIVE",
  "amount": 19.99,
  "currencyCode": "USD",
  "intervalLength": 1,
  "intervalUnit": "MONTH",
  "startDate": "2025-10-01",
  "nextBillingDate": "2025-10-01",
  "totalOccurrences": 12,
  "completedOccurrences": 0,
  "trialOccurrences": 1,
  "paymentProfile": {
    "cardType": "Visa",
    "last4Digits": "1111",
    "expirationDate": "12/2025"
  },
  "createdAt": "2025-09-09T11:00:22"
}
```

### Get Subscription Details

Retrieve details for a specific subscription.

**Endpoint:** `GET /subscriptions/{subscriptionId}`

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/subscriptions/123456789" \
  -H "X-Correlation-ID: corr-id-12353"
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "name": "Premium Plan",
  "status": "ACTIVE",
  "amount": 19.99,
  "currencyCode": "USD",
  "intervalLength": 1,
  "intervalUnit": "MONTH",
  "startDate": "2025-10-01",
  "nextBillingDate": "2025-10-01",
  "totalOccurrences": 12,
  "completedOccurrences": 0,
  "trialOccurrences": 1,
  "paymentProfile": {
    "cardType": "Visa",
    "last4Digits": "1111",
    "expirationDate": "12/2025"
  },
  "createdAt": "2025-09-09T11:00:22"
}
```

### List Customer Subscriptions

List all subscriptions for a specific customer.

**Endpoint:** `GET /subscriptions/customer/{customerId}`

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/subscriptions/customer/cust-12345" \
  -H "X-Correlation-ID: corr-id-12354"
```

**Response:**

```json
[
  {
    "subscriptionId": "123456789",
    "customerId": "cust-12345",
    "name": "Premium Plan",
    "status": "ACTIVE",
    "amount": 19.99,
    "currencyCode": "USD",
    "intervalLength": 1,
    "intervalUnit": "MONTH",
    "nextBillingDate": "2025-10-01"
  },
  {
    "subscriptionId": "987654321",
    "customerId": "cust-12345",
    "name": "Basic Plan",
    "status": "CANCELLED",
    "amount": 9.99,
    "currencyCode": "USD",
    "intervalLength": 1,
    "intervalUnit": "MONTH",
    "nextBillingDate": null
  }
]
```

### Cancel a Subscription

Cancel an existing subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/cancel`

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/123456789/cancel" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12355" \
  -H "X-Correlation-ID: corr-id-12355"
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "status": "CANCELLED",
  "message": "Subscription has been cancelled successfully"
}
```

### Update Subscription Amount

Update the amount of an existing subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/amount`

**Request:**

```json
{
  "amount": 24.99,
  "currencyCode": "USD"
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/123456789/amount" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12356" \
  -H "X-Correlation-ID: corr-id-12356" \
  -d '{
    "amount": 24.99,
    "currencyCode": "USD"
  }'
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "name": "Premium Plan",
  "status": "ACTIVE",
  "amount": 24.99,
  "currencyCode": "USD",
  "message": "Subscription amount updated successfully"
}
```

### Suspend a Subscription

Temporarily suspend an active subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/suspend`

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/123456789/suspend" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12357" \
  -H "X-Correlation-ID: corr-id-12357"
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "status": "SUSPENDED",
  "message": "Subscription has been suspended successfully"
}
```

### Reactivate a Subscription

Reactivate a suspended subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/reactivate`

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/123456789/reactivate" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12358" \
  -H "X-Correlation-ID: corr-id-12358"
```

**Response:**

```json
{
  "subscriptionId": "123456789",
  "customerId": "cust-12345",
  "status": "ACTIVE",
  "message": "Subscription has been reactivated successfully"
}
```

## Webhooks

### Receive Authorize.Net Webhook

Endpoint for receiving webhook notifications from Authorize.Net.

**Endpoint:** `POST /webhooks/authorizenet/notification`

**Request:**

```json
{
  "notificationId": "webhook-notification-12345",
  "eventType": "net.authorize.payment.authcapture.created",
  "eventDate": "2025-09-09T12:00:00Z",
  "webhookId": "webhook-12345",
  "payload": {
    "responseCode": 1,
    "authCode": "ABCD1234",
    "avsResponse": "Y",
    "authAmount": 100.50,
    "entityName": "transaction",
    "id": "60157385847"
  }
}
```

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/webhooks/authorizenet/notification" \
  -H "Content-Type: application/json" \
  -d '{
    "notificationId": "webhook-notification-12345",
    "eventType": "net.authorize.payment.authcapture.created",
    "eventDate": "2025-09-09T12:00:00Z",
    "webhookId": "webhook-12345",
    "payload": {
      "responseCode": 1,
      "authCode": "ABCD1234",
      "avsResponse": "Y",
      "authAmount": 100.50,
      "entityName": "transaction",
      "id": "60157385847"
    }
  }'
```

**Response:**

```json
{
  "webhookId": "webhook-12345",
  "status": "RECEIVED",
  "message": "Webhook notification received and processed successfully"
}
```

### Webhook Health Check

Check the health of the webhook endpoint.

**Endpoint:** `GET /webhooks/authorizenet/health`

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/webhooks/authorizenet/health"
```

**Response:**

```json
{
  "status": "UP",
  "webhookEndpoint": "/webhooks/authorizenet/notification",
  "timestamp": "2025-09-09T12:05:22"
}
```
