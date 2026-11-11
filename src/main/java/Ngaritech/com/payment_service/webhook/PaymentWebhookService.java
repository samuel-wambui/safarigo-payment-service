package Ngaritech.com.payment_service.webhook;

import Ngaritech.com.payment_service.outbox.PaymentOutboxEvent;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEventRepository;
import Ngaritech.com.payment_service.payment.*;
import Ngaritech.com.payment_service.provider.ProviderTransaction;
import Ngaritech.com.payment_service.provider.ProviderTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final String PAYSTACK = "PAYSTACK";

    private final PaystackSignatureVerifier signatureVerifier;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final PaymentOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handlePaystackWebhook(
            String timestampedSignature,
            String legacySignature,
            String payload
    ) {
        if (!signatureVerifier.isValid(timestampedSignature, legacySignature, payload)) {
            throw new AccessDeniedException("Invalid webhook signature");
        }

        String payloadHash = sha256(payload);
        if (webhookEventRepository.existsByPayloadHash(payloadHash)) {
            return;
        }

        JsonNode root = readPayload(payload);
        String eventType = root.path("event").asText(null);
        JsonNode data = root.path("data");
        String reference = data.path("reference").asText(null);

        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(PAYSTACK);
        event.setEventType(eventType);
        event.setProviderReference(reference);
        event.setSignature(firstNonBlank(timestampedSignature, legacySignature));
        event.setPayloadHash(payloadHash);
        event.setPayload(payload);
        event.setValidSignature(true);
        webhookEventRepository.save(event);

        if (reference == null || reference.isBlank()) {
            event.setProcessedAt(Instant.now());
            return;
        }

        paymentRepository.findWithLockingByProviderReference(reference)
                .ifPresent(payment -> processPaymentEvent(payment, event, eventType, data, payload));
        event.setProcessedAt(Instant.now());
    }

    private void processPaymentEvent(
            Payment payment,
            PaymentWebhookEvent event,
            String eventType,
            JsonNode data,
            String payload
    ) {
        String status = data.path("status").asText("");
        boolean chargeSuccessful = "charge.success".equals(eventType) && "success".equalsIgnoreCase(status);
        boolean chargeFailed = "charge.failed".equals(eventType)
                || "failed".equalsIgnoreCase(status)
                || "abandoned".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status);

        ProviderTransaction transaction = new ProviderTransaction();
        transaction.setPaymentId(payment.getId());
        transaction.setProvider(PAYSTACK);
        transaction.setProviderReference(payment.getProviderReference());
        transaction.setProviderTransactionId(data.path("id").asText(null));
        transaction.setStatus(status);
        transaction.setGatewayResponse(data.path("gateway_response").asText(null));
        transaction.setAmountMinor(data.path("amount").asLong(-1));
        transaction.setCurrency(data.path("currency").asText(null));
        transaction.setRawPayload(payload);
        providerTransactionRepository.save(transaction);

        if (chargeSuccessful && payment.getStatus() == PaymentStatus.PENDING
                && paymentCorrelationMatches(payment, data)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(Instant.now());
            emitPaymentEvent(payment, "payment.succeeded");
        }
        else if (chargeSuccessful && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            emitPaymentEvent(payment, "payment.verification_mismatch");
        }
        else if (chargeFailed && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            emitPaymentEvent(payment, "payment.failed");
        }
        event.setProcessedAt(Instant.now());
    }

    private boolean paymentCorrelationMatches(Payment payment, JsonNode data) {
        JsonNode metadata = data.path("metadata");
        if (metadata.isTextual()) {
            try {
                metadata = objectMapper.readTree(metadata.asText());
            }
            catch (Exception ignored) {
                return false;
            }
        }
        return data.path("amount").asLong(-1) == payment.getAmountMinor()
                && payment.getCurrency().equalsIgnoreCase(data.path("currency").asText(""))
                && payment.getBookingId().toString().equals(metadata.path("booking_id").asText())
                && payment.getUserId().toString().equals(metadata.path("iam_user_id").asText())
                && payment.getConversationId().equals(metadata.path("conversation_id").asText());
    }

    private void emitPaymentEvent(Payment payment, String eventType) {
        outboxEventRepository.save(PaymentOutboxEvent.of(
                payment.getId(),
                eventType,
                json(Map.of(
                        "paymentId", payment.getId().toString(),
                        "bookingId", payment.getBookingId().toString(),
                        "conversationId", payment.getConversationId(),
                        "userId", payment.getUserId().toString(),
                        "providerReference", payment.getProviderReference()
                ))
        ));
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("Webhook payload is invalid");
        }
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception exception) {
            return "{}";
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not hash webhook payload", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "missing";
    }
}
