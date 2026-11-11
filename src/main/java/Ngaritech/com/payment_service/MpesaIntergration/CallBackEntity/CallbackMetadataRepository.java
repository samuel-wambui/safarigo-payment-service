package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackMetadataRepository extends JpaRepository<CallbackMetadata, Long> {
}