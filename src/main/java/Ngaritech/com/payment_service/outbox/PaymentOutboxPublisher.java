package Ngaritech.com.payment_service.outbox;

import Ngaritech.com.payment_service.payment.Payment;
import Ngaritech.com.payment_service.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "payment.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {

    private static final List<String> TERMINAL_PAYMENT_EVENTS = List.of(
            "payment.succeeded",
            "payment.failed",
            "payment.verification_mismatch"
    );
    private static final int MAX_ATTEMPTS = 20;

    private final PaymentOutboxEventRepository outboxRepository;
    private final PaymentRepository paymentRepository;
    private final RestClient.Builder restClientBuilder;

    @Scheduled(fixedDelayString = "${payment.notification-publish-delay-ms:2000}")
    @Transactional
    public void publishTerminalPaymentEvents() {
        List<PaymentOutboxEvent> events = outboxRepository
                .findTop50ByStatusAndEventTypeInOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        TERMINAL_PAYMENT_EVENTS
                );
        for (PaymentOutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(PaymentOutboxEvent event) {
        Payment payment = paymentRepository.findById(event.getAggregateId()).orElse(null);
        if (payment == null || payment.getNotificationUrl() == null) {
            failPermanently(event, "payment or notification URL is missing");
            return;
        }
        try {
            restClientBuilder.build()
                    .post()
                    .uri(payment.getNotificationUrl())
                    .header("Content-Type", "application/json")
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setProcessedAt(Instant.now());
        }
        catch (Exception exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                failPermanently(event, exception.getClass().getSimpleName());
            }
            else {
                log.warn(
                        "payment.notification.retry eventId={} paymentId={} attempt={} errorType={}",
                        event.getId(),
                        event.getAggregateId(),
                        attempts,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void failPermanently(PaymentOutboxEvent event, String reason) {
        event.setStatus(OutboxStatus.FAILED);
        event.setProcessedAt(Instant.now());
        log.error(
                "payment.notification.failed eventId={} paymentId={} reason={}",
                event.getId(),
                event.getAggregateId(),
                reason
        );
    }
}
