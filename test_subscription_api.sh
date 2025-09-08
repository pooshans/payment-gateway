#!/bin/bash

# Subscription API Test Script
# This script tests all endpoints of the Subscription API

# Configuration
API_BASE_URL="http://localhost:8080/api"
AUTH_USER="user"
AUTH_PASSWORD="dea1b732-3204-4998-9e69-0d66984eac4f" # Replace with actual password
CUSTOMER_ID="1"

# Generate a correlation ID
CORRELATION_ID="test-$(date +%s)"

echo "=== Testing Subscription API ==="
echo "Using correlation ID: $CORRELATION_ID"
echo

# 1. Create a subscription
echo "=== 1. Creating subscription ==="
SUBSCRIPTION_REQUEST='{
  "cardNumber": "4111111111111111",
  "expirationDate": "12/2030",
  "cardSecurityCode": "123",
  "cardholderName": "John Doe",
  "amount": 49.99,
  "currencyCode": "USD",
  "subscriptionName": "Premium Plan",
  "intervalLength": 1,
  "intervalUnit": "MONTH",
  "totalOccurrences": 12,
  "startDate": "'$(date -v+1d +%Y-%m-%d)'",
  "firstName": "John",
  "lastName": "Doe",
  "company": "Example Corp",
  "address": "123 Main St",
  "city": "Anytown",
  "state": "CA",
  "zip": "12345",
  "country": "US"
}'

CREATE_RESPONSE=$(curl -s -X POST "$API_BASE_URL/subscriptions/customer/$CUSTOMER_ID" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID-create" \
  -u "$AUTH_USER:$AUTH_PASSWORD" \
  -d "$SUBSCRIPTION_REQUEST")

echo "Response: $CREATE_RESPONSE"
echo

# Extract subscription ID from database
echo "=== Getting subscription ID from database ==="
SUBSCRIPTION_ID=$(docker exec -it payment-db psql -U postgres -d paymentdb -t -c "SELECT subscription_id FROM subscriptions WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1;")
SUBSCRIPTION_ID=$(echo $SUBSCRIPTION_ID | xargs)
echo "Subscription ID: $SUBSCRIPTION_ID"
echo

# 2. Get subscription (will likely fail due to LazyInitializationException)
echo "=== 2. Getting subscription by ID ==="
GET_RESPONSE=$(curl -s -X GET "$API_BASE_URL/subscriptions/$SUBSCRIPTION_ID" \
  -H "X-Correlation-ID: $CORRELATION_ID-get" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Response: $GET_RESPONSE"
echo

# 3. Update subscription amount
echo "=== 3. Updating subscription amount ==="
UPDATE_REQUEST='{
  "amount": 59.99,
  "reason": "Price increase"
}'

UPDATE_RESPONSE=$(curl -s -X POST "$API_BASE_URL/subscriptions/$SUBSCRIPTION_ID/amount" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID-update" \
  -u "$AUTH_USER:$AUTH_PASSWORD" \
  -d "$UPDATE_REQUEST")

echo "Response: $UPDATE_RESPONSE"
echo

# 4. Suspend subscription
echo "=== 4. Suspending subscription ==="
SUSPEND_RESPONSE=$(curl -s -X POST "$API_BASE_URL/subscriptions/$SUBSCRIPTION_ID/suspend?reason=Testing%20suspension" \
  -H "X-Correlation-ID: $CORRELATION_ID-suspend" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Response: $SUSPEND_RESPONSE"
echo

# 5. Reactivate subscription
echo "=== 5. Reactivating subscription ==="
REACTIVATE_RESPONSE=$(curl -s -X POST "$API_BASE_URL/subscriptions/$SUBSCRIPTION_ID/reactivate" \
  -H "X-Correlation-ID: $CORRELATION_ID-reactivate" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Response: $REACTIVATE_RESPONSE"
echo

# 6. Cancel subscription
echo "=== 6. Cancelling subscription ==="
CANCEL_RESPONSE=$(curl -s -X POST "$API_BASE_URL/subscriptions/$SUBSCRIPTION_ID/cancel?reason=Testing%20cancellation" \
  -H "X-Correlation-ID: $CORRELATION_ID-cancel" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Response: $CANCEL_RESPONSE"
echo

# 7. Get customer subscriptions (will likely fail due to LazyInitializationException)
echo "=== 7. Getting customer subscriptions ==="
GET_CUSTOMER_SUBS_RESPONSE=$(curl -s -X GET "$API_BASE_URL/subscriptions/customer/$CUSTOMER_ID" \
  -H "X-Correlation-ID: $CORRELATION_ID-get-customer" \
  -u "$AUTH_USER:$AUTH_PASSWORD")

echo "Response: $GET_CUSTOMER_SUBS_RESPONSE"
echo

echo "=== All tests completed ==="
