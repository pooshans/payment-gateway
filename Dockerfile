FROM amazoncorretto:17-alpine as builder

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy pom.xml separately to leverage Docker cache
COPY pom.xml .
# Download dependencies (will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline

# Copy source code
COPY src/ ./src/

# Build the application
RUN mvn clean package -DskipTests

# Runtime image
FROM amazoncorretto:17-alpine

WORKDIR /app

# Add curl for health checks
RUN apk add --no-cache curl jq bash

# Add a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create directory for logs with proper permissions
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

# Copy JAR from builder stage
COPY --from=builder /app/target/payment-gateway-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
COPY healthcheck.sh /app/healthcheck.sh

# Make scripts executable
RUN chmod +x /app/docker-entrypoint.sh /app/healthcheck.sh

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 CMD ["/app/healthcheck.sh"]

# Use entrypoint script to handle startup procedures
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", "-Dsun.misc.unsafe.allowJAXBImplementationAccess=true", "-jar", "app.jar"]
