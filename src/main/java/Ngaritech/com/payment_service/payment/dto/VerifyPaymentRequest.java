package Ngaritech.com.payment_service.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyPaymentRequest(
        @NotNull UUID bookingId,
        @NotBlank @Size(max = 200) String conversationId,
        @NotNull UUID principalUserId
) {
}
