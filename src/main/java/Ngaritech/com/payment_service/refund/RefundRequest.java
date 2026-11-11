package Ngaritech.com.payment_service.refund;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RefundRequest(
        @Min(1)
        long amountMinor,

        @Size(max = 500)
        String reason
) {
}
