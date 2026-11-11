package Ngaritech.com.payment_service.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SandboxPaymentRequest(
        @Min(1)
        long amountMinor,

        @NotBlank
        String currency,

        @Email
        String customerEmail,

        String externalReference,

        String provider
) {
}
