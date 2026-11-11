package Ngaritech.com.payment_service.paystack;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paystack")
public record PaystackProperties(
        String secretKey,
        String webhookSigningSecret,
        String baseUrl,
        String callbackUrl
) {
}
