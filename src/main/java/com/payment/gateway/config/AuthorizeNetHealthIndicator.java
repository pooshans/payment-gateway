package com.payment.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for Authorize.NET connectivity.
 * This component monitors the availability of the Authorize.NET API endpoints,
 * providing real-time health status information for the external payment
 * processor.
 * It's used by Spring Boot Actuator for health checks and contributes to the
 * application's overall readiness state.
 * <p>
 * The indicator performs a basic connectivity check to ensure the API is
 * reachable.
 * It supports both sandbox and production environments based on configuration.
 */
@Component
@Slf4j
public class AuthorizeNetHealthIndicator implements HealthIndicator {

    @Value("${authorize.api.environment:sandbox}")
    private String environment;

    @Override
    public Health health() {
        try {
            String apiUrl = getApiUrl();
            boolean isHealthy = checkConnection(apiUrl);

            if (isHealthy) {
                Map<String, Object> details = new HashMap<>();
                details.put("environment", environment);
                details.put("url", apiUrl);
                return Health.up().withDetails(details).build();
            } else {
                return Health.down()
                        .withDetail("error", "Failed to connect to Authorize.Net")
                        .withDetail("environment", environment)
                        .withDetail("url", apiUrl)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Error checking Authorize.Net health: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("environment", environment)
                    .build();
        }
    }

    private String getApiUrl() {
        if ("production".equalsIgnoreCase(environment)) {
            return "https://api.authorize.net";
        } else {
            return "https://apitest.authorize.net";
        }
    }

    private boolean checkConnection(String apiUrl) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            log.warn("Error connecting to Authorize.Net: {}", e.getMessage());
            return false;
        }
    }
}
