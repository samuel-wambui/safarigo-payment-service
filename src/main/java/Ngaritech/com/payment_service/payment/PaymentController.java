package Ngaritech.com.payment_service.payment;

import Ngaritech.com.payment_service.common.ApiResponse;
import Ngaritech.com.payment_service.payment.dto.CreatePaymentRequest;
import Ngaritech.com.payment_service.payment.dto.CreateMpesaStkPaymentRequest;
import Ngaritech.com.payment_service.payment.dto.MpesaStkPaymentResponse;
import Ngaritech.com.payment_service.payment.dto.VerifyPaymentRequest;
import Ngaritech.com.payment_service.refund.RefundRequest;
import Ngaritech.com.payment_service.refund.RefundResponse;
import Ngaritech.com.payment_service.security.AuthenticatedUser;
import Ngaritech.com.payment_service.security.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('SCOPE_payment:create') and (hasRole('TRAVELLER') or hasRole('USER'))")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = authenticatedUserResolver.resolve(jwt);
        PaymentResponse response = paymentService.createPayment(request, user, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Payment initialized", response));
    }

    @PostMapping("/payments/mpesa/stk")
    @PreAuthorize("hasAuthority('SCOPE_payment:create')")
    public ResponseEntity<ApiResponse<MpesaStkPaymentResponse>> createMpesaStkPayment(
            @Valid @RequestBody CreateMpesaStkPaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = jwt == null ? null : authenticatedUserResolver.resolve(jwt);
        MpesaStkPaymentResponse response = paymentService.createMpesaStkPayment(request, user, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("M-Pesa STK push sent", response));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = authenticatedUserResolver.resolve(jwt);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", paymentService.getPayment(paymentId, user)));
    }

    @GetMapping("/internal/payments/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_service:n8n') and hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getInternalPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", paymentService.getInternalPayment(paymentId)));
    }

    @PostMapping("/internal/payments/{paymentId}/verify")
    @PreAuthorize("hasAuthority('SCOPE_service:n8n') and hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyInternalPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment verified",
                paymentService.verifyInternalPayment(paymentId, request)
        ));
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('SCOPE_payment:refund') and hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = authenticatedUserResolver.resolve(jwt);
        RefundResponse response = paymentService.requestRefund(paymentId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Refund requested", response));
    }

    @GetMapping(value = "/payments/paystack/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paystackCallback() {
        return ResponseEntity.ok("""
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Payment received</title></head>
                <body style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;margin:2rem;color:#102a43">
                <h1>Payment callback received</h1>
                <p>You can now return to WhatsApp. We will confirm payment after the secure provider notification is processed.</p>
                </body>
                </html>
                """);
    }
}
