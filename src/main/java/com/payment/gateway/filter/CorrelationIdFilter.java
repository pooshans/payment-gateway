package com.payment.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter responsible for correlation ID management across all API
 * requests.
 * 
 * This filter handles the extraction, generation, and propagation of
 * correlation IDs,
 * which are crucial for request tracing in a distributed system. For each
 * incoming request,
 * it will:
 * 
 * 1. Extract any existing correlation ID from the request headers
 * 2. Generate a new correlation ID if none exists
 * 3. Add the correlation ID to the Mapped Diagnostic Context (MDC) for logging
 * 4. Add the correlation ID to the response headers
 * 5. Link the correlation ID with distributed tracing systems
 * 
 * Having an @Order(1) annotation ensures this filter runs early in the filter
 * chain,
 * making the correlation ID available throughout request processing.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final Tracer tracer;

    @Autowired
    public CorrelationIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        // If no correlation ID was provided, generate one
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Set the correlation ID in the MDC context
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        // Add to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // Add to tracing span
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(CORRELATION_ID_MDC_KEY, correlationId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
