package Ngaritech.com.payment_service.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRateQuote(
        String provider,
        LocalDate date,
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate
) {
}
