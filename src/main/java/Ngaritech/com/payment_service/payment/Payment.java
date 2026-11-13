package Ngaritech.com.payment_service.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false, length = 200)
    private String conversationId;

    @Column(nullable = false, length = 254)
    private String paystackCustomerEmail;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long sourceAmountMinor;

    @Column(nullable = false, length = 3)
    private String sourceCurrency;

    @Column(precision = 19, scale = 8)
    private BigDecimal fxRate;

    private LocalDate fxRateDate;

    @Column(length = 50)
    private String fxProvider;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, unique = true, length = 100)
    private String providerReference;

    @Column(length = 120)
    private String accessCode;

    @Column(length = 120)
    private String idempotencyKey;

    @Column(length = 160)
    private String paymentPurpose;

    @Column(length = 1000)
    private String authorizationUrl;

    @Column(length = 1000)
    private String notificationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Column(length = 120)
    private String createdByActor;

    private Instant paidAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getPaystackCustomerEmail() {
        return paystackCustomerEmail;
    }

    public void setPaystackCustomerEmail(String paystackCustomerEmail) {
        this.paystackCustomerEmail = paystackCustomerEmail;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getSourceAmountMinor() {
        return sourceAmountMinor;
    }

    public void setSourceAmountMinor(long sourceAmountMinor) {
        this.sourceAmountMinor = sourceAmountMinor;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public LocalDate getFxRateDate() {
        return fxRateDate;
    }

    public void setFxRateDate(LocalDate fxRateDate) {
        this.fxRateDate = fxRateDate;
    }

    public String getFxProvider() {
        return fxProvider;
    }

    public void setFxProvider(String fxProvider) {
        this.fxProvider = fxProvider;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPaymentPurpose() {
        return paymentPurpose;
    }

    public void setPaymentPurpose(String paymentPurpose) {
        this.paymentPurpose = paymentPurpose;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getCreatedByActor() {
        return createdByActor;
    }

    public void setCreatedByActor(String createdByActor) {
        this.createdByActor = createdByActor;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
