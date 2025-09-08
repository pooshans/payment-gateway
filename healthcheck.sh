#!/bin/bash
set -e

# Health check script for the payment gateway application

# Check if the application is responding
HEALTH_URL="http://localhost:8080/api/actuator/health"
HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)

if [ $HTTP_RESPONSE -eq 200 ]; then
    # Optionally check the detailed health status
    HEALTH_STATUS=$(curl -s $HEALTH_URL | jq -r '.status')
    
    if [ "$HEALTH_STATUS" = "UP" ]; then
        echo "Application is healthy: $HEALTH_STATUS"
        exit 0
    else
        echo "Application is reporting unhealthy status: $HEALTH_STATUS"
        exit 1
    fi
else
    echo "Application health check failed with HTTP status: $HTTP_RESPONSE"
    exit 1
fi
