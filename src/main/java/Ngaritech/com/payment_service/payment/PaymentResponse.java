package Ngaritech.com.payment_service.payment;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID userId,
        UUID bookingId,
        String conversationId,
        long amountMinor,
        String currency,
        long sourceAmountMinor,
        String sourceCurrency,
        BigDecimal fxRate,
        LocalDate fxRateDate,
        String fxProvider,
        String provider,
        String providerReference,
        String idempotencyKey,
        String paymentPurpose,
        String authorizationUrl,
        PaymentStatus status,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getBookingId(),
                payment.getConversationId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getSourceAmountMinor(),
                payment.getSourceCurrency(),
                payment.getFxRate(),
                payment.getFxRateDate(),
                payment.getFxProvider(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getIdempotencyKey(),
                payment.getPaymentPurpose(),
                payment.getAuthorizationUrl(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
