package Ngaritech.com.payment_service.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository

public interface RefundRepository extends JpaRepository<Refund, UUID> {
}
