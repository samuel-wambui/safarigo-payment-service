package Ngaritech.com.payment_service.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    List<PaymentOutboxEvent> findTop50ByStatusAndEventTypeInOrderByCreatedAtAsc(
            OutboxStatus status,
            Collection<String> eventTypes
    );
}
