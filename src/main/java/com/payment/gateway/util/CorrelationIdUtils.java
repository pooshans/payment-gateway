package com.payment.gateway.util;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class CorrelationIdUtils {

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Get the current correlation ID from the MDC
     * 
     * @return the correlation ID or "UNKNOWN" if not found
     */
    public String getCurrentCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        return correlationId != null ? correlationId : "UNKNOWN";
    }

    /**
     * Set a correlation ID in the MDC
     * 
     * @param correlationId the correlation ID to set
     */
    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }
    }

    /**
     * Clear the correlation ID from the MDC
     */
    public void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_MDC_KEY);
    }
}
