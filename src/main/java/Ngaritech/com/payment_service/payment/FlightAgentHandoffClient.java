package Ngaritech.com.payment_service.payment;

import java.util.UUID;

public interface FlightAgentHandoffClient {
    FlightAgentPaymentHandoff validate(
            UUID principalUserId,
            String conversationId,
            UUID bookingId
    );
}
