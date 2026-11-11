package Ngaritech.com.payment_service.webhook;

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
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(length = 120)
    private String eventType;

    @Column(length = 100)
    private String providerReference;

    @Column(nullable = false, length = 1000)
    private String signature;

    @Column(nullable = false, length = 128)
    private String payloadHash;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean validSignature;

    private Instant processedAt;

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

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setValidSignature(boolean validSignature) {
        this.validSignature = validSignature;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
