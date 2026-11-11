package Ngaritech.com.payment_service.paystack;

public record PaystackVerification(
        String providerTransactionId,
        String reference,
        String status,
        long amountMinor,
        String currency,
        String gatewayResponse,
        String rawPayload
) {
}
