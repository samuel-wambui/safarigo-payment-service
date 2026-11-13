package Ngaritech.com.payment_service.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxQuoteRequest(
        @NotBlank
        @Size(max = 50)
        String provider,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String baseCurrency,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String quoteCurrency,

        @NotNull
        @DecimalMin(value = "0.000001")
        BigDecimal rate,

        @NotNull
        LocalDate rateDate,

        @Positive
        long sourceAmountMinor,

        @Positive
        long convertedAmountMinor
) {
}
