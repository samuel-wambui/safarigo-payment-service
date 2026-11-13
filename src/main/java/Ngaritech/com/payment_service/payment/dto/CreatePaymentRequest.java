package Ngaritech.com.payment_service.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull
        UUID bookingId,

        @NotBlank
        @Size(max = 200)
        String conversationId,

        @NotBlank
        @Size(max = 1000)
        String notificationUrl,

        @Valid
        FxQuoteRequest fxQuote
) {
}
