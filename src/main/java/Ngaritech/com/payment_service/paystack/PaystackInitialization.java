package Ngaritech.com.payment_service.paystack;

public record PaystackInitialization(
        String authorizationUrl,
        String accessCode,
        String reference
) {
}
