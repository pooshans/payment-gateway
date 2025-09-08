package com.payment.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentGatewayApplicationTests {

    @Test
    void contextLoads() {
        // This test will fail if the Spring application context cannot be loaded
    }
}
