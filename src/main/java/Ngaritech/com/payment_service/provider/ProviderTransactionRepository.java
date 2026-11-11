package Ngaritech.com.payment_service.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, UUID> {
}
