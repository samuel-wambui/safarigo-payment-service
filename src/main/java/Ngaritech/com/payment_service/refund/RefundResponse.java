package Ngaritech.com.payment_service.refund;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID userId,
        long amountMinor,
        String reason,
        RefundStatus status,
        String providerReference,
        Instant createdAt
) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getUserId(),
                refund.getAmountMinor(),
                refund.getReason(),
                refund.getStatus(),
                refund.getProviderReference(),
                refund.getCreatedAt()
        );
    }
}
