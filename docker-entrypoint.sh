#!/bin/bash
set -e

# Print environment info
echo "Starting Payment Gateway Application..."
echo "Environment: $SPRING_PROFILES_ACTIVE"
echo "Java version: $(java -version 2>&1 | head -n 1)"

# Wait for database to be available
if [ "$WAIT_FOR_DB" = "true" ]; then
  echo "Waiting for database to be available..."
  # Use pg_isready for PostgreSQL
  while ! nc -z payment-db 5432; do
    echo "Database is not available yet - waiting..."
    sleep 2
  done
  echo "Database is up and running!"
fi

# Wait for Redis if configured
if [ "$WAIT_FOR_REDIS" = "true" ]; then
  echo "Waiting for Redis to be available..."
  while ! nc -z redis 6379; do
    echo "Redis is not available yet - waiting..."
    sleep 2
  done
  echo "Redis is up and running!"
fi

# Apply database migrations if configured
if [ "$RUN_MIGRATIONS" = "true" ]; then
  echo "Running database migrations..."
  # For Flyway migrations, Spring Boot will handle this automatically
  # with spring.flyway.enabled=true
  echo "Database migrations will be applied by Spring Boot on startup"
fi

# Add JVM options for better container support
JVM_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Set up proper handling of SIGTERM for graceful shutdown
trap 'kill -TERM $PID' TERM INT

# Start application with exec to replace shell process
echo "Starting application..."
exec java $JVM_OPTS -Dsun.misc.unsafe.allowJAXBImplementationAccess=true -jar app.jar "$@" &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
echo "Application exited with status $EXIT_STATUS"
exit $EXIT_STATUS
