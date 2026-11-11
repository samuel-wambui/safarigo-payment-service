package Ngaritech.com.payment_service.payment;

import java.util.UUID;

public record FlightAgentPaymentHandoff(
        String conversationId,
        UUID principalUserId,
        UUID bookingId,
        String orderType,
        String amount,
        long amountMinor,
        String currency,
        String selectedOfferId
) {
}
