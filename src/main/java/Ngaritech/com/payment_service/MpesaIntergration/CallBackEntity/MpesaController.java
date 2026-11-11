package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;

import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionAsyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.InternalB2CTransactionRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.*;
import Ngaritech.com.payment_service.MpesaIntergration.Register_URL.RegisterUrlResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Services.AcknowledgeResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Services.DarajaApi;
import Ngaritech.com.payment_service.common.ApiResponse;
import Ngaritech.com.payment_service.payment.PaymentService;
import Ngaritech.com.payment_service.payment.dto.CreateMpesaStkPaymentRequest;
import Ngaritech.com.payment_service.payment.dto.MpesaStkPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/api/v1/mpesa")
public class MpesaController {

    private final MpesaCallbackService mpesaCallbackService;
    private final DarajaApi darajaApi;
    private final AcknowledgeResponse acknowledgeResponse;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @Autowired
    public MpesaController(DarajaApi darajaApi, AcknowledgeResponse acknowledgeResponse, ObjectMapper objectMapper, MpesaCallbackService mpesaCallbackService, PaymentService paymentService) {
        this.darajaApi = darajaApi;
        this.acknowledgeResponse = acknowledgeResponse;
        this.objectMapper = objectMapper;
        this.mpesaCallbackService = mpesaCallbackService;
        this.paymentService = paymentService;
    }

    @PostMapping("/callback")
    public ResponseEntity<String> handleMpesaCallback(@RequestBody StkPushAsyncResponse response) {
        try {
            log.info("Received M-Pesa callback: " + objectMapper.writeValueAsString(response));
            mpesaCallbackService.saveMpesaCallback(response);
            return ResponseEntity.ok("Callback received and processed");
        } catch (Exception e) {
            log.error("Error processing M-Pesa callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing callback");
        }
    }

    @PostMapping(path = "/register-url", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegisterUrlResponse> registerUrl() {
        return ResponseEntity.ok(darajaApi.registerUrl());
    }

    @PostMapping(path = "/c2b-transaction", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<C2BResponse> C2BTransaction(@RequestBody C2BRequest c2BRequest) {
        log.info("Customer payment underway. Wait for confirmation");
        return ResponseEntity.ok(darajaApi.C2BTransaction(c2BRequest));
    }

    @PostMapping(path = "/b2c-transaction", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<B2CTransactionSyncResponse> b2CTransaction(@RequestBody InternalB2CTransactionRequest internalB2CTransactionRequest) {
        log.info(".......Initiating B2C Transaction......");
        return ResponseEntity.ok(darajaApi.b2CTransaction(internalB2CTransactionRequest));
    }

    @PostMapping(path = "/b2c-queue-timeout", produces = "application/json")
    public ResponseEntity<AcknowledgeResponse> queueTimeout(@RequestBody Object object) {
        return ResponseEntity.ok(acknowledgeResponse);
    }

    @PostMapping(path = "/b2c-transaction-result", produces = "application/json")
    public ResponseEntity<AcknowledgeResponse> b2cTransactionAsyncResults(@RequestBody B2CTransactionAsyncResponse b2CTransactionAsyncResponse) {
        log.info(".......B2C Transaction Response......");
        log.info(safeJson(b2CTransactionAsyncResponse));
        return ResponseEntity.ok(acknowledgeResponse);
    }

    @PostMapping(path = "/stk-transaction-request", produces = "application/json")
    @PreAuthorize("hasAuthority('SCOPE_payment:create')")
    public ResponseEntity<ApiResponse<MpesaStkPaymentResponse>> stkPushTransaction(
            @RequestBody InternalStkPushRequest internalStkPushRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        String paymentPurpose = firstNonBlank(
                internalStkPushRequest.getPaymentPurpose(),
                internalStkPushRequest.getTransactionType(),
                "Travel payment"
        );
        CreateMpesaStkPaymentRequest request = new CreateMpesaStkPaymentRequest(
                null,
                Math.round(internalStkPushRequest.getTransactionAmount() * 100),
                internalStkPushRequest.getTransactionNumber(),
                internalStkPushRequest.getTransactionType(),
                null,
                paymentPurpose
        );
        MpesaStkPaymentResponse response = paymentService.createMpesaStkPayment(request, null, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("M-Pesa STK push sent", response));
    }

    @PostMapping(path = "/stk-transaction-result", produces = "application/json")
    public ResponseEntity<AcknowledgeResponse> acknowledgeStkPushResponse(@RequestBody StkPushAsyncResponse stkPushAsyncResponse) {
        log.info("====STK Push Async Response====");
        log.info(safeJson(stkPushAsyncResponse));

        try {
            mpesaCallbackService.saveMpesaCallback(stkPushAsyncResponse);
        }
        catch (Exception exception) {
            log.error("Error processing M-Pesa STK callback", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(acknowledgeResponse);
        }

        return ResponseEntity.ok(acknowledgeResponse);
    }

    @PostMapping(path = "/stk-transaction-status", produces = "application/json")
    @PreAuthorize("hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<StkPushStatusResponse> stkPushStatus(@RequestBody InternalStkPushStatusRequest internalStkPushStatusRequest) {
        return ResponseEntity.ok(darajaApi.stkPushStatus(internalStkPushStatusRequest, "default"));
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            return value == null ? "null" : value.toString();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
