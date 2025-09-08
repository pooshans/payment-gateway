package com.payment.gateway.config;

import net.authorize.Environment;
import net.authorize.api.contract.v1.MerchantAuthenticationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthorizeNetConfig {

    @Value("${authorize.api.login-id}")
    private String apiLoginId;

    @Value("${authorize.api.transaction-key}")
    private String transactionKey;

    @Value("${authorize.api.environment}")
    private String environment;

    @Value("${authorize.api.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${authorize.api.read-timeout:30000}")
    private int readTimeout;

    /**
     * Creates merchant authentication object for Authorize.Net API
     * 
     * @return MerchantAuthenticationType with API credentials
     */
    @Bean
    public MerchantAuthenticationType merchantAuthentication() {
        MerchantAuthenticationType merchantAuth = new MerchantAuthenticationType();
        merchantAuth.setName(apiLoginId);
        merchantAuth.setTransactionKey(transactionKey);
        return merchantAuth;
    }

    /**
     * Determines which Authorize.Net environment to use
     * 
     * @return Environment.SANDBOX or Environment.PRODUCTION
     */
    @Bean
    public Environment authorizeNetEnvironment() {
        return "production".equalsIgnoreCase(environment) ? Environment.PRODUCTION : Environment.SANDBOX;
    }
}
