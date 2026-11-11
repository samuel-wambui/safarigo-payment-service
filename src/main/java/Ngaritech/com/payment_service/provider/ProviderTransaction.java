package Ngaritech.com.payment_service.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "provider_transactions")
public class ProviderTransaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 100)
    private String providerReference;

    @Column(length = 120)
    private String providerTransactionId;

    @Column(length = 60)
    private String status;

    @Column(length = 500)
    private String gatewayResponse;

    private Long amountMinor;

    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

}
