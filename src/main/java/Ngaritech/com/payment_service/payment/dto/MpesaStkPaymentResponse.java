package Ngaritech.com.payment_service.payment.dto;

import Ngaritech.com.payment_service.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record MpesaStkPaymentResponse(
        UUID paymentId,
        UUID userId,
        UUID bookingId,
        long amountMinor,
        String currency,
        String provider,
        String providerReference,
        String idempotencyKey,
        String paymentPurpose,
        String merchantRequestId,
        String checkoutRequestId,
        String responseCode,
        String responseDescription,
        String customerMessage,
        PaymentStatus status,
        boolean idempotentReplay,
        Instant createdAt,
        Instant updatedAt
) {
}
