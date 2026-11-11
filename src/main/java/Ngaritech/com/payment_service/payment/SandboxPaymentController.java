package Ngaritech.com.payment_service.payment;

import Ngaritech.com.payment_service.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sandbox/payments")
@RequiredArgsConstructor
public class SandboxPaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_sandbox:payment:create')")
    public ResponseEntity<ApiResponse<PaymentResponse>> createSandboxPayment(
            @Valid @RequestBody SandboxPaymentRequest request,
            Authentication authentication
    ) {
        PaymentResponse response = paymentService.createSandboxPayment(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Sandbox payment created", response));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_service:sandbox') and hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getSandboxPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Sandbox payment retrieved", paymentService.getInternalPayment(paymentId)));
    }
}
