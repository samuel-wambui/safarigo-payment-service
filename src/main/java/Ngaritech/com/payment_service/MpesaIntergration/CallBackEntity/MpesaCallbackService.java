package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.ItemItem;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkCallback;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushAsyncResponse;
import Ngaritech.com.payment_service.payment.Payment;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEvent;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEventRepository;
import Ngaritech.com.payment_service.payment.PaymentRepository;
import Ngaritech.com.payment_service.payment.PaymentStatus;
import Ngaritech.com.payment_service.provider.ProviderTransaction;
import Ngaritech.com.payment_service.provider.ProviderTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaCallbackService {

    private static final String MPESA = "MPESA";

    private final MpesaCallbackRepository mpesaCallbackRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final PaymentOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MpesaCallback saveMpesaCallback(StkPushAsyncResponse response) throws ParseException {
        StkCallback stkCallback = requireStkCallback(response);
        log.info(
                "mpesa.stk.callback_received merchantRequestId={} checkoutRequestId={} resultCode={} resultDesc={}",
                stkCallback.getMerchantRequestID(),
                stkCallback.getCheckoutRequestID(),
                stkCallback.getResultCode(),
                stkCallback.getResultDesc()
        );

        MpesaCallback mpesaCallback = new MpesaCallback();
        mpesaCallback.setMerchantRequestID(stkCallback.getMerchantRequestID());
        mpesaCallback.setCheckoutRequestID(stkCallback.getCheckoutRequestID());
        mpesaCallback.setResultCode(stkCallback.getResultCode());
        mpesaCallback.setResultDesc(stkCallback.getResultDesc());

        CallbackMetadata callbackMetadata = new CallbackMetadata();
        callbackMetadata.setMpesaCallback(mpesaCallback);

        if (stkCallback.getCallbackMetadata() != null && stkCallback.getCallbackMetadata().getItem() != null) {
            List<CallbackMetadataItem> items = new ArrayList<>();
            for (ItemItem item : stkCallback.getCallbackMetadata().getItem()) {
                CallbackMetadataItem metadataItem = new CallbackMetadataItem();
                metadataItem.setName(item.getName());
                metadataItem.setValue(item.getValue() == null ? "" : item.getValue().toString());
                metadataItem.setCallbackMetadata(callbackMetadata);
                items.add(metadataItem);
            }
            callbackMetadata.setItems(items);
        }

        mpesaCallback.setCallbackMetadata(callbackMetadata);

        MpesaCallback savedCallback = mpesaCallbackRepository.save(mpesaCallback);
        updatePaymentFromCallback(response, stkCallback);

        return savedCallback;
    }

    private void updatePaymentFromCallback(StkPushAsyncResponse response, StkCallback stkCallback) {
        var paymentResult = paymentRepository.findWithLockingByAccessCode(stkCallback.getCheckoutRequestID());
        if (paymentResult.isEmpty()) {
            log.warn(
                    "mpesa.stk.callback_unmatched checkoutRequestId={} merchantRequestId={} resultCode={}",
                    stkCallback.getCheckoutRequestID(),
                    stkCallback.getMerchantRequestID(),
                    stkCallback.getResultCode()
            );
            return;
        }

        paymentResult.ifPresent(payment -> {
                    PaymentStatus nextStatus = stkCallback.getResultCode() == 0
                            ? PaymentStatus.SUCCESS
                            : PaymentStatus.FAILED;

                    log.info(
                            "mpesa.stk.callback_matched_payment paymentId={} userId={} providerReference={} checkoutRequestId={} previousStatus={} nextStatus={} resultCode={}",
                            payment.getId(),
                            payment.getUserId(),
                            payment.getProviderReference(),
                            stkCallback.getCheckoutRequestID(),
                            payment.getStatus(),
                            nextStatus,
                            stkCallback.getResultCode()
                    );

                    payment.setStatus(nextStatus);
                    if (nextStatus == PaymentStatus.SUCCESS && payment.getPaidAt() == null) {
                        payment.setPaidAt(Instant.now());
                        log.info(
                                "mpesa.stk.payment_successful paymentId={} userId={} amountMinor={} currency={} receipt={} paidAt={}",
                                payment.getId(),
                                payment.getUserId(),
                                payment.getAmountMinor(),
                                payment.getCurrency(),
                                firstMetadataValue(stkCallback, "MpesaReceiptNumber", stkCallback.getCheckoutRequestID()),
                                payment.getPaidAt()
                        );
                    }
                    else if (nextStatus == PaymentStatus.FAILED) {
                        log.info(
                                "mpesa.stk.payment_failed paymentId={} userId={} resultCode={} resultDesc={}",
                                payment.getId(),
                                payment.getUserId(),
                                stkCallback.getResultCode(),
                                stkCallback.getResultDesc()
                        );
                    }

                    providerTransactionRepository.save(providerTransaction(payment, stkCallback, response));

                    outboxEventRepository.save(PaymentOutboxEvent.of(
                            payment.getId(),
                            nextStatus == PaymentStatus.SUCCESS ? "payment.succeeded" : "payment.failed",
                            json(Map.of(
                                    "paymentId", payment.getId().toString(),
                                    "provider", MPESA,
                                    "providerReference", payment.getProviderReference(),
                                    "checkoutRequestId", Objects.toString(stkCallback.getCheckoutRequestID(), ""),
                                    "resultCode", stkCallback.getResultCode()
                            ))
                    ));
                });
    }

    private ProviderTransaction providerTransaction(Payment payment, StkCallback stkCallback, StkPushAsyncResponse response) {
        ProviderTransaction transaction = new ProviderTransaction();
        transaction.setPaymentId(payment.getId());
        transaction.setProvider(MPESA);
        transaction.setProviderReference(payment.getProviderReference());
        transaction.setProviderTransactionId(firstMetadataValue(stkCallback, "MpesaReceiptNumber", stkCallback.getCheckoutRequestID()));
        transaction.setStatus(String.valueOf(stkCallback.getResultCode()));
        transaction.setGatewayResponse(stkCallback.getResultDesc());
        transaction.setAmountMinor(payment.getAmountMinor());
        transaction.setCurrency(payment.getCurrency());
        transaction.setRawPayload(json(response));
        return transaction;
    }

    private StkCallback requireStkCallback(StkPushAsyncResponse response) {
        if (response == null || response.getBody() == null || response.getBody().getStkCallback() == null) {
            throw new IllegalArgumentException("Invalid M-Pesa callback payload");
        }
        return response.getBody().getStkCallback();
    }

    private String firstMetadataValue(StkCallback stkCallback, String name, String fallback) {
        if (stkCallback.getCallbackMetadata() == null || stkCallback.getCallbackMetadata().getItem() == null) {
            return fallback;
        }

        return stkCallback.getCallbackMetadata().getItem().stream()
                .filter(item -> name.equals(item.getName()))
                .map(ItemItem::getValue)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(fallback);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            return "{}";
        }
    }
}
