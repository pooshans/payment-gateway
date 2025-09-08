# Payment Gateway API Specification

This document provides the comprehensive technical specification for the Payment Gateway API endpoints, including request/response formats, authentication requirements, and examples.

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

| Header | Description | Required |
|--------|-------------|----------|
| `X-Request-ID` | A unique identifier for the request to ensure idempotency | Yes |
| `X-Correlation-ID` | A correlation ID for tracing requests through the system | Yes |

## Payment Operations

### Process a Payment (Purchase)

Process a complete payment (combined authorization and capture).

**Endpoint:** `POST /payments/purchase`

**Request Schema:**

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

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `cardNumber` | String | Yes | The credit card number |
| `expirationDate` | String | Yes | Card expiration date in MM/YYYY format |
| `cardSecurityCode` | String | Yes | CVV/CVC security code |
| `cardholderName` | String | Yes | Name as it appears on the card |
| `amount` | Number | Yes | Transaction amount |
| `currencyCode` | String | Yes | 3-letter ISO currency code (e.g., USD) |
| `firstName` | String | Yes | Customer's first name |
| `lastName` | String | Yes | Customer's last name |
| `address` | String | No | Billing address street |
| `city` | String | No | Billing address city |
| `state` | String | No | Billing address state/province |
| `zip` | String | No | Billing address postal code |
| `country` | String | No | Billing address country code (2-letter ISO) |
| `orderDescription` | String | No | Description of the order |
| `orderNumber` | String | No | Merchant-defined order reference |

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

**Response Schema (Success):**

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

**Response Parameters (Success):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | Unique identifier for the transaction |
| `authCode` | String | Authorization code from the payment processor |
| `status` | String | Transaction status (APPROVED, DECLINED, ERROR) |
| `responseCode` | String | Response code from the payment processor |
| `responseMessage` | String | Human-readable response message |
| `amount` | Number | Transaction amount |
| `currencyCode` | String | 3-letter ISO currency code |
| `cardType` | String | Type of card used (Visa, Mastercard, etc.) |
| `last4Digits` | String | Last 4 digits of the card number |
| `transactionDate` | String | ISO 8601 date-time of the transaction |
| `correlationId` | String | The correlation ID from the request |
| `isAuthorized` | Boolean | Whether the transaction was authorized |
| `isCaptured` | Boolean | Whether the funds were captured |

**Response Schema (Error):**

```json
{
  "status": "ERROR",
  "responseCode": "2",
  "responseMessage": "This transaction has been declined.",
  "correlationId": "corr-id-12345"
}
```

**Response Parameters (Error):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Error status (ERROR, DECLINED) |
| `responseCode` | String | Error code from the payment processor |
| `responseMessage` | String | Human-readable error message |
| `correlationId` | String | The correlation ID from the request |

**Status Codes:**

| Status Code | Description |
|-------------|-------------|
| 200 | Successful transaction |
| 400 | Invalid request parameters |
| 402 | Payment required (transaction declined) |
| 422 | Unprocessable entity (validation error) |
| 500 | Internal server error |

### Authorize a Payment

Authorize a payment without capturing funds.

**Endpoint:** `POST /payments/authorize`

**Request Schema:**

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

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `cardNumber` | String | Yes | The credit card number |
| `expirationDate` | String | Yes | Card expiration date in MM/YYYY format |
| `cardSecurityCode` | String | Yes | CVV/CVC security code |
| `cardholderName` | String | Yes | Name as it appears on the card |
| `amount` | Number | Yes | Transaction amount |
| `currencyCode` | String | Yes | 3-letter ISO currency code (e.g., USD) |
| `firstName` | String | Yes | Customer's first name |
| `lastName` | String | Yes | Customer's last name |
| `address` | String | No | Billing address street |
| `city` | String | No | Billing address city |
| `state` | String | No | Billing address state/province |
| `zip` | String | No | Billing address postal code |
| `country` | String | No | Billing address country code (2-letter ISO) |

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

**Response Schema (Success):**

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
  "correlationId": "corr-id-12346",
  "isAuthorized": true,
  "isCaptured": false
}
```

**Response Parameters, Status Codes:**
Same as the purchase endpoint, except `isCaptured` will be `false`.

### Capture an Authorized Payment

Capture funds for a previously authorized transaction.

**Endpoint:** `POST /payments/{transactionId}/capture`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | The transaction ID of the authorization to capture |

**Request Schema:**

```json
{
  "amount": 100.50
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `amount` | Number | No | Amount to capture (defaults to full authorized amount if omitted) |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/60157385847/capture" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12347" \
  -H "X-Correlation-ID: corr-id-12347" \
  -d '{
    "amount": 100.50
  }'
```

**Response Schema (Success):**

```json
{
  "transactionId": "60157385847",
  "authCode": "ABCD1234",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "transactionDate": "2025-09-09T11:15:30",
  "correlationId": "corr-id-12347",
  "isAuthorized": true,
  "isCaptured": true
}
```

**Response Parameters, Status Codes:**
Similar to the purchase endpoint.

### Void/Cancel a Transaction

Cancel a previously authorized transaction that has not been settled.

**Endpoint:** `POST /payments/{transactionId}/void`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | The transaction ID to void |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/60157385847/void" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12348" \
  -H "X-Correlation-ID: corr-id-12348"
```

**Response Schema (Success):**

```json
{
  "transactionId": "60157385847",
  "status": "VOIDED",
  "responseCode": "1",
  "responseMessage": "This transaction has been voided.",
  "transactionDate": "2025-09-09T11:30:15",
  "correlationId": "corr-id-12348"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | Unique identifier for the transaction |
| `status` | String | Transaction status (should be VOIDED) |
| `responseCode` | String | Response code from the payment processor |
| `responseMessage` | String | Human-readable response message |
| `transactionDate` | String | ISO 8601 date-time of the void operation |
| `correlationId` | String | The correlation ID from the request |

### Refund a Transaction

Refund a previously settled transaction.

**Endpoint:** `POST /payments/{transactionId}/refund`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | The transaction ID to refund |

**Request Schema:**

```json
{
  "amount": 100.50,
  "reason": "Customer requested refund"
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `amount` | Number | No | Amount to refund (defaults to full amount if omitted) |
| `reason` | String | No | Reason for the refund |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/payments/60157385847/refund" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12349" \
  -H "X-Correlation-ID: corr-id-12349" \
  -d '{
    "amount": 100.50,
    "reason": "Customer requested refund"
  }'
```

**Response Schema (Success):**

```json
{
  "transactionId": "60157385847",
  "refundTransactionId": "60157385850",
  "status": "APPROVED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "transactionDate": "2025-09-09T12:00:00",
  "correlationId": "corr-id-12349"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | Original transaction ID that was refunded |
| `refundTransactionId` | String | New transaction ID for the refund |
| `status` | String | Transaction status (APPROVED, ERROR) |
| `responseCode` | String | Response code from the payment processor |
| `responseMessage` | String | Human-readable response message |
| `amount` | Number | Refund amount |
| `currencyCode` | String | 3-letter ISO currency code |
| `transactionDate` | String | ISO 8601 date-time of the refund |
| `correlationId` | String | The correlation ID from the request |

### Partial Refund

Refund a portion of a previously settled transaction.

**Endpoint:** `POST /payments/{transactionId}/partial-refund`

This endpoint behaves the same as the refund endpoint but requires the amount parameter.

### Get Transaction Details

Retrieve details about a specific transaction.

**Endpoint:** `GET /payments/{transactionId}`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | The transaction ID to retrieve |

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/payments/60157385847" \
  -H "X-Request-ID: unique-request-id-12350" \
  -H "X-Correlation-ID: corr-id-12350"
```

**Response Schema (Success):**

```json
{
  "transactionId": "60157385847",
  "authCode": "ABCD1234",
  "status": "SETTLED",
  "responseCode": "1",
  "responseMessage": "This transaction has been approved.",
  "amount": 100.50,
  "currencyCode": "USD",
  "cardType": "Visa",
  "last4Digits": "1111",
  "transactionDate": "2025-09-09T10:30:45",
  "settlementDate": "2025-09-10",
  "correlationId": "corr-id-12350",
  "isAuthorized": true,
  "isCaptured": true,
  "isSettled": true,
  "customerDetails": {
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US"
  }
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `transactionId` | String | Unique identifier for the transaction |
| `authCode` | String | Authorization code from the payment processor |
| `status` | String | Current transaction status |
| `responseCode` | String | Response code from the payment processor |
| `responseMessage` | String | Human-readable response message |
| `amount` | Number | Transaction amount |
| `currencyCode` | String | 3-letter ISO currency code |
| `cardType` | String | Type of card used (Visa, Mastercard, etc.) |
| `last4Digits` | String | Last 4 digits of the card number |
| `transactionDate` | String | ISO 8601 date-time of the transaction |
| `settlementDate` | String | Date when the transaction was settled |
| `correlationId` | String | The correlation ID from the request |
| `isAuthorized` | Boolean | Whether the transaction was authorized |
| `isCaptured` | Boolean | Whether the funds were captured |
| `isSettled` | Boolean | Whether the transaction has been settled |
| `customerDetails` | Object | Details about the customer |

## Recurring Payments/Subscriptions

### Create a Subscription

Create a new recurring payment subscription.

**Endpoint:** `POST /subscriptions`

**Request Schema:**

```json
{
  "cardNumber": "4111111111111111",
  "expirationDate": "12/2025",
  "cardSecurityCode": "123",
  "cardholderName": "John Doe",
  "amount": 19.99,
  "currencyCode": "USD",
  "firstName": "John",
  "lastName": "Doe",
  "address": "123 Main St",
  "city": "Boston",
  "state": "MA",
  "zip": "02108",
  "country": "US",
  "interval": "MONTHLY",
  "startDate": "2025-10-01",
  "totalOccurrences": 12,
  "trialOccurrences": 1,
  "trialAmount": 0.00,
  "name": "Gold Plan Subscription",
  "description": "Monthly subscription for Gold Plan"
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `cardNumber` | String | Yes | The credit card number |
| `expirationDate` | String | Yes | Card expiration date in MM/YYYY format |
| `cardSecurityCode` | String | Yes | CVV/CVC security code |
| `cardholderName` | String | Yes | Name as it appears on the card |
| `amount` | Number | Yes | Subscription amount per interval |
| `currencyCode` | String | Yes | 3-letter ISO currency code (e.g., USD) |
| `firstName` | String | Yes | Customer's first name |
| `lastName` | String | Yes | Customer's last name |
| `address` | String | No | Billing address street |
| `city` | String | No | Billing address city |
| `state` | String | No | Billing address state/province |
| `zip` | String | No | Billing address postal code |
| `country` | String | No | Billing address country code |
| `interval` | String | Yes | Billing interval (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY) |
| `startDate` | String | No | ISO 8601 date to start subscription (defaults to current date) |
| `totalOccurrences` | Integer | No | Total number of billing occurrences (null for unlimited) |
| `trialOccurrences` | Integer | No | Number of trial occurrences |
| `trialAmount` | Number | No | Amount to charge during trial period |
| `name` | String | Yes | Name of the subscription |
| `description` | String | No | Description of the subscription |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12351" \
  -H "X-Correlation-ID: corr-id-12351" \
  -d '{
    "cardNumber": "4111111111111111",
    "expirationDate": "12/2025",
    "cardSecurityCode": "123",
    "cardholderName": "John Doe",
    "amount": 19.99,
    "currencyCode": "USD",
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US",
    "interval": "MONTHLY",
    "startDate": "2025-10-01",
    "totalOccurrences": 12,
    "name": "Gold Plan Subscription"
  }'
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "ACTIVE",
  "name": "Gold Plan Subscription",
  "amount": 19.99,
  "currencyCode": "USD",
  "interval": "MONTHLY",
  "startDate": "2025-10-01",
  "nextBillingDate": "2025-10-01",
  "totalOccurrences": 12,
  "pastOccurrences": 0,
  "cardType": "Visa",
  "last4Digits": "1111",
  "customerDetails": {
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US"
  },
  "correlationId": "corr-id-12351"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Subscription status (ACTIVE, SUSPENDED, CANCELLED) |
| `name` | String | Name of the subscription |
| `amount` | Number | Subscription amount per interval |
| `currencyCode` | String | 3-letter ISO currency code |
| `interval` | String | Billing interval |
| `startDate` | String | ISO 8601 date when subscription starts |
| `nextBillingDate` | String | ISO 8601 date of next billing |
| `totalOccurrences` | Integer | Total number of billing occurrences |
| `pastOccurrences` | Integer | Number of completed billing occurrences |
| `cardType` | String | Type of card used (Visa, Mastercard, etc.) |
| `last4Digits` | String | Last 4 digits of the card number |
| `customerDetails` | Object | Details about the customer |
| `correlationId` | String | The correlation ID from the request |

### Get Subscription Details

Retrieve details about a specific subscription.

**Endpoint:** `GET /subscriptions/{subscriptionId}`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | The subscription ID to retrieve |

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/subscriptions/12345" \
  -H "X-Request-ID: unique-request-id-12352" \
  -H "X-Correlation-ID: corr-id-12352"
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "ACTIVE",
  "name": "Gold Plan Subscription",
  "description": "Monthly subscription for Gold Plan",
  "amount": 19.99,
  "currencyCode": "USD",
  "interval": "MONTHLY",
  "startDate": "2025-10-01",
  "nextBillingDate": "2025-11-01",
  "totalOccurrences": 12,
  "pastOccurrences": 1,
  "cardType": "Visa",
  "last4Digits": "1111",
  "customerDetails": {
    "firstName": "John",
    "lastName": "Doe",
    "address": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zip": "02108",
    "country": "US"
  },
  "transactions": [
    {
      "transactionId": "60157385860",
      "date": "2025-10-01T00:00:00",
      "amount": 19.99,
      "status": "SETTLED"
    }
  ],
  "correlationId": "corr-id-12352"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Subscription status |
| `name` | String | Name of the subscription |
| `description` | String | Description of the subscription |
| `amount` | Number | Subscription amount per interval |
| `currencyCode` | String | 3-letter ISO currency code |
| `interval` | String | Billing interval |
| `startDate` | String | ISO 8601 date when subscription started |
| `nextBillingDate` | String | ISO 8601 date of next billing |
| `totalOccurrences` | Integer | Total number of billing occurrences |
| `pastOccurrences` | Integer | Number of completed billing occurrences |
| `cardType` | String | Type of card used |
| `last4Digits` | String | Last 4 digits of the card number |
| `customerDetails` | Object | Details about the customer |
| `transactions` | Array | List of transactions associated with this subscription |
| `correlationId` | String | The correlation ID from the request |

### List Customer Subscriptions

Retrieve all subscriptions for a specific customer.

**Endpoint:** `GET /customers/{customerId}/subscriptions`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | String | The customer ID to retrieve subscriptions for |

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Filter by subscription status (optional) |
| `page` | Integer | Page number for pagination (default: 1) |
| `pageSize` | Integer | Number of items per page (default: 20, max: 100) |

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/customers/C123456/subscriptions?status=ACTIVE" \
  -H "X-Request-ID: unique-request-id-12353" \
  -H "X-Correlation-ID: corr-id-12353"
```

**Response Schema (Success):**

```json
{
  "subscriptions": [
    {
      "subscriptionId": "12345",
      "status": "ACTIVE",
      "name": "Gold Plan Subscription",
      "amount": 19.99,
      "currencyCode": "USD",
      "interval": "MONTHLY",
      "nextBillingDate": "2025-11-01"
    },
    {
      "subscriptionId": "12346",
      "status": "ACTIVE",
      "name": "Premium Support",
      "amount": 9.99,
      "currencyCode": "USD",
      "interval": "MONTHLY",
      "nextBillingDate": "2025-11-15"
    }
  ],
  "pagination": {
    "currentPage": 1,
    "pageSize": 20,
    "totalPages": 1,
    "totalItems": 2
  },
  "correlationId": "corr-id-12353"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptions` | Array | List of subscription summaries |
| `pagination` | Object | Pagination details |
| `correlationId` | String | The correlation ID from the request |

### Cancel a Subscription

Cancel an active subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/cancel`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | The subscription ID to cancel |

**Request Schema:**

```json
{
  "reason": "Customer requested cancellation",
  "cancelImmediately": true
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `reason` | String | No | Reason for cancellation |
| `cancelImmediately` | Boolean | No | Whether to cancel immediately (true) or at the end of current billing period (false, default) |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/12345/cancel" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12354" \
  -H "X-Correlation-ID: corr-id-12354" \
  -d '{
    "reason": "Customer requested cancellation",
    "cancelImmediately": true
  }'
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "CANCELLED",
  "name": "Gold Plan Subscription",
  "cancellationDate": "2025-10-15T14:30:00",
  "correlationId": "corr-id-12354"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Updated subscription status (should be CANCELLED) |
| `name` | String | Name of the subscription |
| `cancellationDate` | String | ISO 8601 date-time of cancellation |
| `correlationId` | String | The correlation ID from the request |

### Update Subscription Amount

Update the amount for an existing subscription.

**Endpoint:** `PUT /subscriptions/{subscriptionId}/amount`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | The subscription ID to update |

**Request Schema:**

```json
{
  "amount": 29.99,
  "effectiveDate": "2025-11-01"
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `amount` | Number | Yes | New subscription amount |
| `effectiveDate` | String | No | ISO 8601 date when new amount should take effect (defaults to next billing cycle) |

**Example curl command:**

```bash
curl -X PUT "http://localhost:8080/subscriptions/12345/amount" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12355" \
  -H "X-Correlation-ID: corr-id-12355" \
  -d '{
    "amount": 29.99,
    "effectiveDate": "2025-11-01"
  }'
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "ACTIVE",
  "name": "Gold Plan Subscription",
  "previousAmount": 19.99,
  "newAmount": 29.99,
  "currencyCode": "USD",
  "effectiveDate": "2025-11-01",
  "nextBillingDate": "2025-11-01",
  "correlationId": "corr-id-12355"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Subscription status |
| `name` | String | Name of the subscription |
| `previousAmount` | Number | Previous subscription amount |
| `newAmount` | Number | Updated subscription amount |
| `currencyCode` | String | 3-letter ISO currency code |
| `effectiveDate` | String | ISO 8601 date when new amount takes effect |
| `nextBillingDate` | String | ISO 8601 date of next billing |
| `correlationId` | String | The correlation ID from the request |

### Suspend a Subscription

Temporarily suspend an active subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/suspend`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | The subscription ID to suspend |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/12345/suspend" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12356" \
  -H "X-Correlation-ID: corr-id-12356"
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "SUSPENDED",
  "name": "Gold Plan Subscription",
  "suspensionDate": "2025-10-15T16:45:00",
  "correlationId": "corr-id-12356"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Updated subscription status (should be SUSPENDED) |
| `name` | String | Name of the subscription |
| `suspensionDate` | String | ISO 8601 date-time of suspension |
| `correlationId` | String | The correlation ID from the request |

### Reactivate a Subscription

Reactivate a suspended subscription.

**Endpoint:** `POST /subscriptions/{subscriptionId}/reactivate`

**URL Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | The subscription ID to reactivate |

**Example curl command:**

```bash
curl -X POST "http://localhost:8080/subscriptions/12345/reactivate" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: unique-request-id-12357" \
  -H "X-Correlation-ID: corr-id-12357"
```

**Response Schema (Success):**

```json
{
  "subscriptionId": "12345",
  "status": "ACTIVE",
  "name": "Gold Plan Subscription",
  "reactivationDate": "2025-10-16T09:15:00",
  "nextBillingDate": "2025-11-01",
  "correlationId": "corr-id-12357"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `subscriptionId` | String | Unique identifier for the subscription |
| `status` | String | Updated subscription status (should be ACTIVE) |
| `name` | String | Name of the subscription |
| `reactivationDate` | String | ISO 8601 date-time of reactivation |
| `nextBillingDate` | String | ISO 8601 date of next billing |
| `correlationId` | String | The correlation ID from the request |

## Webhooks

### Receive Authorize.Net Webhook

Endpoint to receive webhook notifications from Authorize.Net.

**Endpoint:** `POST /webhooks/authorize-net`

**Request Schema:**
The webhook payload format follows Authorize.Net's webhook notification structure.

**Response Schema (Success):**

```json
{
  "received": true,
  "eventType": "net.authorize.payment.authcapture.created",
  "timestamp": "2025-10-15T17:30:00"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `received` | Boolean | Whether the webhook was successfully received |
| `eventType` | String | The type of event that was received |
| `timestamp` | String | ISO 8601 date-time the webhook was processed |

### Webhook Health Check

Check if the webhook endpoint is functioning correctly.

**Endpoint:** `GET /webhooks/health`

**Example curl command:**

```bash
curl -X GET "http://localhost:8080/webhooks/health" \
  -H "X-Request-ID: unique-request-id-12358" \
  -H "X-Correlation-ID: corr-id-12358"
```

**Response Schema (Success):**

```json
{
  "status": "UP",
  "message": "Webhook endpoint is active and ready to receive notifications"
}
```

**Response Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Current status of the webhook endpoint (UP, DOWN) |
| `message` | String | Additional information about the webhook endpoint status |
