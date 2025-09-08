package com.payment.gateway.config;

import com.payment.gateway.util.CorrelationIdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    private final CorrelationIdUtils correlationIdUtils;

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        String correlationId = correlationIdUtils.getCurrentCorrelationId();

        // Add the correlation ID to outgoing requests
        if (!request.getHeaders().containsKey(CORRELATION_ID_HEADER)) {
            request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        }

        return execution.execute(request, body);
    }
}
