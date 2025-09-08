#!/bin/bash

# Test script for webhook endpoints

# Configuration
API_BASE_URL="http://localhost:8080/api"
AUTH_USER="user"
AUTH_PASSWORD="dea1b732-3204-4998-9e69-0d66984eac4f" # Replace with actual password

# Generate a correlation ID
CORRELATION_ID="test-$(date +%s)"

echo "=== Testing Webhook Endpoints ==="
echo "Using correlation ID: $CORRELATION_ID"
echo

# 1. Test the health check endpoint
echo "=== 1. Testing webhook health check endpoint ==="
HEALTH_RESPONSE=$(curl -s -X GET \
  "$API_BASE_URL/webhooks/authorizenet/health" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Health check response: $HEALTH_RESPONSE"
echo

# 2. Test payment webhook
echo "=== 2. Testing payment webhook ==="
PAYMENT_RESPONSE=$(curl -s -X POST \
  "$API_BASE_URL/webhooks/authorizenet" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID-payment" \
  -u "$AUTH_USER:$AUTH_PASSWORD" \
  -d @sample-webhook-payload.json)

echo "Payment webhook response: $PAYMENT_RESPONSE"
echo

# 3. Test subscription webhook
echo "=== 3. Testing subscription webhook ==="
SUBSCRIPTION_RESPONSE=$(curl -s -X POST \
  "$API_BASE_URL/webhooks/authorizenet" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID-subscription" \
  -u "$AUTH_USER:$AUTH_PASSWORD" \
  -d @sample-subscription-webhook.json)

echo "Subscription webhook response: $SUBSCRIPTION_RESPONSE"
echo

# 4. Verify webhook events in database
echo "=== 4. Verifying webhook events in database ==="
echo "To check the database, run:"
echo "docker exec -it payment-db psql -U postgres -d paymentdb -c \"SELECT * FROM webhook_events ORDER BY received_at DESC LIMIT 5;\""
echo

echo "=== All tests completed ==="
