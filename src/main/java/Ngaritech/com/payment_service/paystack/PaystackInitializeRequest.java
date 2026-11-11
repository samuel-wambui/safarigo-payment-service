package Ngaritech.com.payment_service.paystack;

import java.util.Map;

public record PaystackInitializeRequest(
        String email,
        String amount,
        String currency,
        String reference,
        String callback_url,
        Map<String, Object> metadata
) {
}
