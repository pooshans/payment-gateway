# Webhook Implementation for Payment Gateway

This document provides information about the webhook implementation for the Payment Gateway, specifically focused on handling Authorize.Net webhook notifications.

## Webhook Endpoints

### Authorize.Net Webhook
- **URL**: `/api/webhooks/authorizenet`
- **Method**: `POST`
- **Purpose**: Receives webhook notifications from Authorize.Net for payment and subscription events

### Health Check Endpoint
- **URL**: `/api/webhooks/authorizenet/health`
- **Method**: `GET`
- **Purpose**: Allows Authorize.Net to verify that the webhook endpoint is operational

## Supported Event Types

The webhook handler supports the following Authorize.Net event types:

1. **Payment Events**
   - `net.authorize.payment.authcapture.created` - Payment authorization and capture event
   - `net.authorize.payment.refund.created` - Refund created event

2. **Subscription Events**
   - `net.authorize.customer.subscription.created` - Subscription created event
   - `net.authorize.customer.subscription.updated` - Subscription updated event
   - `net.authorize.customer.subscription.suspended` - Subscription suspended event
   - `net.authorize.customer.subscription.terminated` - Subscription terminated event
   - `net.authorize.customer.subscription.expiring` - Subscription expiring notification
   - `net.authorize.customer.subscription.expired` - Subscription expired event

## Webhook Security

Webhooks are secured using signature validation. The signature is verified using the webhook signature key configured in the application properties.

## Testing the Webhook

You can test the webhook endpoint using the sample payload files included in this repository:

1. **Payment Webhook**: `sample-webhook-payload.json`
2. **Subscription Webhook**: `sample-subscription-webhook.json`

### Testing with cURL

```bash
# Test payment webhook
curl -X POST "http://localhost:8080/api/webhooks/authorizenet" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-webhook-123" \
  -d @sample-webhook-payload.json

# Test subscription webhook
curl -X POST "http://localhost:8080/api/webhooks/authorizenet" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-webhook-456" \
  -d @sample-subscription-webhook.json

# Test health check endpoint
curl -X GET "http://localhost:8080/api/webhooks/authorizenet/health"
```

## Webhook Processing

Webhooks are processed asynchronously:

1. When a webhook is received, it is immediately stored in the database
2. A response is sent to Authorize.Net acknowledging receipt
3. The webhook is then processed in the background
4. Failed webhook processing is retried automatically

## Database Structure

Webhooks are stored in the `webhook_events` table with the following columns:

- `id`: Internal database ID
- `event_id`: Unique event ID from the webhook notification
- `event_type`: Type of event (e.g., payment.authcapture.created)
- `event_source`: Source of the event (AUTHORIZE_NET)
- `payload`: Full JSON payload as text
- `signature`: Webhook signature for verification
- `signature_valid`: Indicates if the signature was valid
- `processed`: Indicates if the webhook was processed successfully
- `processing_attempts`: Number of processing attempts
- `last_error`: Last error message if processing failed
- `correlation_id`: Correlation ID for tracking
- `related_payment_id`: Associated payment ID
- `related_subscription_id`: Associated subscription ID
- `related_customer_id`: Associated customer ID
- `received_at`: Timestamp when webhook was received
- `processed_at`: Timestamp when webhook was processed

## Configuration

The webhook signature key should be configured in the application properties:

```yaml
payment:
  gateway:
    webhook:
      authorizenet:
        signature-key: your-webhook-signature-key
```

For security, it's recommended to set the signature key as an environment variable:

```
WEBHOOK_SIGNATURE_KEY=your-webhook-signature-key
```
