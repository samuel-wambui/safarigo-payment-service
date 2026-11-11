package Ngaritech.com.payment_service.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateMpesaStkPaymentRequest(
        UUID bookingId,

        @Min(1)
        long amountMinor,

        @NotBlank
        String phoneNumber,

        @Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$")
        String transactionType,

        String customerEmail,

        @Pattern(regexp = "^[A-Za-z0-9 _.,:/#-]{1,160}$")
        String paymentPurpose
) {
}
