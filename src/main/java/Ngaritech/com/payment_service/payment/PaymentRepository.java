package Ngaritech.com.payment_service.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderReference(String providerReference);

    Optional<Payment> findByProviderAndIdempotencyKey(String provider, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockingByProviderReference(String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockingById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockingByAccessCode(String accessCode);
}
