package Ngaritech.com.payment_service.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    boolean existsByPayloadHash(String payloadHash);
}
