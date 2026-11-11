package Ngaritech.com.payment_service.webhook;

import Ngaritech.com.payment_service.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/paystack")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> handlePaystackWebhook(
            @RequestHeader(name = "Paystack-Signature", required = false) String timestampedSignature,
            @RequestHeader(name = "x-paystack-signature", required = false) String legacySignature,
            @RequestBody String payload
    ) {
        paymentWebhookService.handlePaystackWebhook(timestampedSignature, legacySignature, payload);
        return ResponseEntity.ok(ApiResponse.success("Webhook accepted", null));
    }
}
