package Ngaritech.com.payment_service.payment;

import Ngaritech.com.payment_service.common.ResourceNotFoundException;
import Ngaritech.com.payment_service.fx.FxRateClient;
import Ngaritech.com.payment_service.fx.FxRateQuote;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Services.DarajaApi;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEvent;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEventRepository;
import Ngaritech.com.payment_service.payment.dto.CreatePaymentRequest;
import Ngaritech.com.payment_service.payment.dto.FxQuoteRequest;
import Ngaritech.com.payment_service.payment.dto.CreateMpesaStkPaymentRequest;
import Ngaritech.com.payment_service.payment.dto.MpesaStkPaymentResponse;
import Ngaritech.com.payment_service.paystack.PaystackClient;
import Ngaritech.com.payment_service.paystack.PaystackInitialization;
import Ngaritech.com.payment_service.paystack.PaystackInitializeRequest;
import Ngaritech.com.payment_service.paystack.PaystackVerification;
import Ngaritech.com.payment_service.payment.dto.VerifyPaymentRequest;
import Ngaritech.com.payment_service.provider.ProviderTransaction;
import Ngaritech.com.payment_service.provider.ProviderTransactionRepository;
import Ngaritech.com.payment_service.refund.*;
import Ngaritech.com.payment_service.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String PAYSTACK = "PAYSTACK";
    private static final String MPESA = "MPESA";
    private static final UUID SANDBOX_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID SANDBOX_BOOKING_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentOutboxEventRepository outboxEventRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final PaystackClient paystackClient;
    private final FlightAgentHandoffClient flightAgentHandoffClient;
    private final FxRateClient fxRateClient;
    private final PaymentNotificationUrlPolicy notificationUrlPolicy;
    private final DarajaApi darajaApi;
    private final ObjectMapper objectMapper;

    @Value("${paystack.callback-url}")
    private String paystackCallbackUrl;

    @Transactional
    public PaymentResponse createPayment(
            CreatePaymentRequest request,
            AuthenticatedUser user,
            String idempotencyKey
    ) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String notificationUrl = notificationUrlPolicy.requireAllowed(request.notificationUrl());
        String verifiedEmail = user.verifiedEmailOptional()
                .orElseThrow(() -> new AccessDeniedException("Verified IAM email is required for payment"));
        var existingPayment = paymentRepository.findByProviderAndIdempotencyKey(
                PAYSTACK,
                normalizedIdempotencyKey
        );
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            requireSamePaymentRequest(existing, request, user);
            existing.setNotificationUrl(notificationUrl);
            return PaymentResponse.from(existing);
        }

        FlightAgentPaymentHandoff handoff = flightAgentHandoffClient.validate(
                user.userId(),
                request.conversationId(),
                request.bookingId()
        );
        requireMatchingHandoff(handoff, request, user);
        if (!"INSTANT".equals(handoff.orderType())) {
            throw new IllegalArgumentException("Paystack payment is only valid for an INSTANT booking handoff");
        }
        ChargeAmount charge = resolveChargeAmount(handoff, request.fxQuote());
        String reference = "pay_" + UUID.randomUUID().toString().replace("-", "");

        Payment payment = new Payment();
        payment.setUserId(user.userId());
        payment.setBookingId(request.bookingId());
        payment.setConversationId(request.conversationId());
        payment.setPaystackCustomerEmail(verifiedEmail);
        payment.setAmountMinor(charge.amountMinor());
        payment.setCurrency(charge.currency());
        payment.setSourceAmountMinor(charge.sourceAmountMinor());
        payment.setSourceCurrency(charge.sourceCurrency());
        payment.setFxRate(charge.fxRate());
        payment.setFxRateDate(charge.fxRateDate());
        payment.setFxProvider(charge.fxProvider());
        payment.setProvider(PAYSTACK);
        payment.setProviderReference(reference);
        payment.setIdempotencyKey(normalizedIdempotencyKey);
        payment.setNotificationUrl(notificationUrl);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedByActor(firstNonBlank(user.actor(), user.username()));
        Payment saved = paymentRepository.saveAndFlush(payment);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("payment_reference", reference);
        metadata.put("booking_id", request.bookingId().toString());
        metadata.put("iam_user_id", user.userId().toString());
        metadata.put("conversation_id", request.conversationId());
        metadata.put("source_amount_minor", charge.sourceAmountMinor());
        metadata.put("source_currency", charge.sourceCurrency());
        metadata.put("charged_amount_minor", charge.amountMinor());
        metadata.put("charged_currency", charge.currency());
        if (charge.fxRate() != null) {
            metadata.put("fx_provider", charge.fxProvider());
            metadata.put("fx_rate", charge.fxRate().toPlainString());
            metadata.put("fx_rate_date", charge.fxRateDate().toString());
        }

        PaystackInitialization initialization = paystackClient.initialize(new PaystackInitializeRequest(
                verifiedEmail,
                Long.toString(charge.amountMinor()),
                charge.currency(),
                reference,
                paystackCallbackUrl,
                metadata
        ));
        if (!reference.equals(initialization.reference())) {
            throw new IllegalStateException("Paystack returned a different transaction reference");
        }
        saved.setAccessCode(initialization.accessCode());
        saved.setAuthorizationUrl(initialization.authorizationUrl());
        outboxEventRepository.save(PaymentOutboxEvent.of(
                saved.getId(),
                "payment.initialized",
                json(Map.of(
                        "paymentId", saved.getId().toString(),
                        "userId", saved.getUserId().toString(),
                        "bookingId", saved.getBookingId().toString(),
                        "conversationId", saved.getConversationId(),
                        "providerReference", saved.getProviderReference()
                ))
        ));

        return PaymentResponse.from(saved);
    }

    @Transactional
    public MpesaStkPaymentResponse createMpesaStkPayment(CreateMpesaStkPaymentRequest request, AuthenticatedUser user, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (request.amountMinor() % 100 != 0) {
            throw new IllegalArgumentException("M-Pesa amount must be a whole KES amount");
        }

        long amountKes = request.amountMinor() / 100;
        paymentRepository.findByProviderAndIdempotencyKey(MPESA, normalizedIdempotencyKey)
                .ifPresent(existing -> log.info(
                        "mpesa.stk.idempotent_replay paymentId={} userId={} providerReference={} checkoutRequestId={} status={} idempotencyKey={}",
                        existing.getId(),
                        existing.getUserId(),
                        existing.getProviderReference(),
                        existing.getAccessCode(),
                        existing.getStatus(),
                        normalizedIdempotencyKey
                ));

        var existingPayment = paymentRepository.findByProviderAndIdempotencyKey(MPESA, normalizedIdempotencyKey);
        if (existingPayment.isPresent()) {
            return mpesaResponse(existingPayment.get(), null, "IDEMPOTENT_REPLAY",
                    "Existing M-Pesa payment returned for this idempotency key",
                    "Existing payment request returned",
                    true);
        }

        String reference = "mpesa_" + UUID.randomUUID().toString().replace("-", "");
        String actor = user == null ? "sandbox-integrator" : firstNonBlank(user.actor(), user.username(), "iam-user");
        UUID userId = user == null ? SANDBOX_USER_ID : user.userId();
        UUID bookingId = request.bookingId() == null ? SANDBOX_BOOKING_ID : request.bookingId();
        String paymentPurpose = normalizePaymentPurpose(request.paymentPurpose(), request.transactionType());

        log.info(
                "mpesa.stk.request_received amountKes={} amountMinor={} phone={} paymentPurpose={} userId={} bookingId={} actor={} idempotencyKey={}",
                amountKes,
                request.amountMinor(),
                maskPhone(request.phoneNumber()),
                paymentPurpose,
                userId,
                bookingId,
                actor,
                normalizedIdempotencyKey
        );

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setBookingId(bookingId);
        payment.setConversationId("mpesa:" + bookingId);
        payment.setPaystackCustomerEmail(user == null
                ? firstNonBlank(request.customerEmail(), "mpesa-customer@payment-service.local")
                : firstNonBlank(user.verifiedEmailOptional().orElse(null), "mpesa-customer@payment-service.local"));
        payment.setAmountMinor(request.amountMinor());
        payment.setCurrency("KES");
        payment.setSourceAmountMinor(request.amountMinor());
        payment.setSourceCurrency("KES");
        payment.setProvider(MPESA);
        payment.setProviderReference(reference);
        payment.setIdempotencyKey(normalizedIdempotencyKey);
        payment.setPaymentPurpose(paymentPurpose);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedByActor(actor);

        Payment saved = paymentRepository.save(payment);
        log.info(
                "mpesa.stk.payment_created paymentId={} userId={} providerReference={} paymentPurpose={} status={} idempotencyKey={}",
                saved.getId(),
                saved.getUserId(),
                saved.getProviderReference(),
                saved.getPaymentPurpose(),
                saved.getStatus(),
                normalizedIdempotencyKey
        );

        InternalStkPushRequest stkRequest = new InternalStkPushRequest();
        stkRequest.setTransactionAmount((double) amountKes);
        stkRequest.setTransactionNumber(request.phoneNumber());
        stkRequest.setPaymentPurpose(paymentPurpose);
        stkRequest.setTransactionCode(reference);

        StkPushSyncResponse darajaResponse;
        try {
            log.info(
                    "mpesa.stk.daraja_request paymentId={} providerReference={} amountKes={} phone={} callbackPending=true",
                    saved.getId(),
                    saved.getProviderReference(),
                    amountKes,
                    maskPhone(request.phoneNumber())
            );
            darajaResponse = darajaApi.stkPushTransaction(stkRequest);
        }
        catch (IOException exception) {
            log.error("mpesa.stk.daraja_io_error paymentId={} providerReference={} message={}",
                    saved.getId(),
                    saved.getProviderReference(),
                    exception.getMessage());
            throw new IllegalStateException("M-Pesa STK push could not be sent", exception);
        }
        saved.setAccessCode(darajaResponse.getCheckoutRequestID());
        log.info(
                "mpesa.stk.daraja_accepted paymentId={} userId={} providerReference={} merchantRequestId={} checkoutRequestId={} responseCode={} responseDescription={}",
                saved.getId(),
                saved.getUserId(),
                saved.getProviderReference(),
                darajaResponse.getMerchantRequestID(),
                darajaResponse.getCheckoutRequestID(),
                darajaResponse.getResponseCode(),
                darajaResponse.getResponseDescription()
        );

        outboxEventRepository.save(PaymentOutboxEvent.of(
                saved.getId(),
                "mpesa.stk_push_requested",
                json(Map.of(
                        "paymentId", saved.getId().toString(),
                        "userId", saved.getUserId().toString(),
                        "bookingId", saved.getBookingId().toString(),
                        "providerReference", saved.getProviderReference(),
                        "idempotencyKey", normalizedIdempotencyKey,
                        "paymentPurpose", saved.getPaymentPurpose(),
                        "checkoutRequestId", firstNonBlank(darajaResponse.getCheckoutRequestID(), ""),
                        "merchantRequestId", firstNonBlank(darajaResponse.getMerchantRequestID(), "")
                ))
        ));

        return mpesaResponse(saved, darajaResponse.getMerchantRequestID(), darajaResponse.getResponseCode(),
                darajaResponse.getResponseDescription(), darajaResponse.getCustomerMessage(), false);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, AuthenticatedUser user) {
        Payment payment = findPayment(paymentId);
        if (!payment.getUserId().equals(user.userId()) && !canReadAnyPayment(user)) {
            throw new AccessDeniedException("Access is denied");
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getInternalPayment(UUID paymentId) {
        return PaymentResponse.from(findPayment(paymentId));
    }

    @Transactional
    public PaymentResponse verifyInternalPayment(UUID paymentId, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findWithLockingById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        requirePaymentCorrelation(payment, request);
        if (!PAYSTACK.equals(payment.getProvider())) {
            throw new IllegalArgumentException("Only Paystack payments can use this verification endpoint");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentResponse.from(payment);
        }

        PaystackVerification verification = paystackClient.verify(payment.getProviderReference());
        recordProviderTransaction(payment, verification);
        applyVerifiedStatus(payment, verification);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public void verifyPaystackCallback(String reference) {
        if (reference == null || reference.isBlank()) {
            return;
        }
        Payment payment = paymentRepository.findWithLockingByProviderReference(reference).orElse(null);
        if (payment == null || !PAYSTACK.equals(payment.getProvider())
                || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        PaystackVerification verification = paystackClient.verify(payment.getProviderReference());
        recordProviderTransaction(payment, verification);
        applyVerifiedStatus(payment, verification);
    }

    @Transactional
    public PaymentResponse createSandboxPayment(SandboxPaymentRequest request, String actor) {
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        String provider = firstNonBlank(request.provider(), "SANDBOX").trim().toUpperCase(Locale.ROOT);
        String reference = firstNonBlank(request.externalReference(), "sandbox_" + UUID.randomUUID().toString().replace("-", ""));

        Payment payment = new Payment();
        payment.setUserId(SANDBOX_USER_ID);
        payment.setBookingId(SANDBOX_BOOKING_ID);
        payment.setConversationId("sandbox:" + SANDBOX_BOOKING_ID);
        payment.setPaystackCustomerEmail(firstNonBlank(request.customerEmail(), "sandbox@payment-service.local"));
        payment.setAmountMinor(request.amountMinor());
        payment.setCurrency(currency);
        payment.setSourceAmountMinor(request.amountMinor());
        payment.setSourceCurrency(currency);
        payment.setProvider(provider);
        payment.setProviderReference(reference);
        payment.setAccessCode("sandbox");
        payment.setAuthorizationUrl(null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedByActor(firstNonBlank(actor, "sandbox-integrator"));

        Payment saved = paymentRepository.save(payment);
        outboxEventRepository.save(PaymentOutboxEvent.of(
                saved.getId(),
                "sandbox.payment.created",
                json(Map.of(
                        "paymentId", saved.getId().toString(),
                        "providerReference", saved.getProviderReference(),
                        "actor", saved.getCreatedByActor()
                ))
        ));

        return PaymentResponse.from(saved);
    }

    @Transactional
    public RefundResponse requestRefund(UUID paymentId, RefundRequest request, AuthenticatedUser user) {
        Payment payment = findPayment(paymentId);

        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setUserId(user.userId());
        refund.setAmountMinor(request.amountMinor());
        refund.setReason(request.reason());
        refund.setStatus(RefundStatus.PENDING);

        Refund saved = refundRepository.save(refund);
        outboxEventRepository.save(PaymentOutboxEvent.of(
                payment.getId(),
                "payment.refund_requested",
                json(Map.of(
                        "refundId", saved.getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "requestedBy", user.userId().toString(),
                        "amountMinor", saved.getAmountMinor()
                ))
        ));

        return RefundResponse.from(saved);
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private boolean canReadAnyPayment(AuthenticatedUser user) {
        return user.hasRole("ADMIN") || user.hasRole("SUPER_ADMIN");
    }

    private void requireSamePaymentRequest(
            Payment payment,
            CreatePaymentRequest request,
            AuthenticatedUser user
    ) {
        if (!payment.getUserId().equals(user.userId())
                || !payment.getBookingId().equals(request.bookingId())
                || !payment.getConversationId().equals(request.conversationId())) {
            throw new AccessDeniedException("Idempotency-Key belongs to a different payment request");
        }
    }

    private void requireMatchingHandoff(
            FlightAgentPaymentHandoff handoff,
            CreatePaymentRequest request,
            AuthenticatedUser user
    ) {
        if (!handoff.principalUserId().equals(user.userId())
                || !handoff.bookingId().equals(request.bookingId())
                || !handoff.conversationId().equals(request.conversationId())
                || handoff.amountMinor() <= 0
                || handoff.currency() == null
                || !handoff.currency().matches("^[A-Z]{3}$")) {
            throw new AccessDeniedException("Flight-agent payment handoff does not match the authenticated request");
        }
    }

    private void requirePaymentCorrelation(Payment payment, VerifyPaymentRequest request) {
        if (!payment.getUserId().equals(request.principalUserId())
                || !payment.getBookingId().equals(request.bookingId())
                || !payment.getConversationId().equals(request.conversationId())) {
            throw new AccessDeniedException("Payment does not belong to the supplied booking context");
        }
    }

    private ChargeAmount resolveChargeAmount(
            FlightAgentPaymentHandoff handoff,
            FxQuoteRequest submittedQuote
    ) {
        String sourceCurrency = handoff.currency().trim().toUpperCase(Locale.ROOT);
        if ("KES".equals(sourceCurrency)) {
            return new ChargeAmount(
                    handoff.amountMinor(),
                    "KES",
                    handoff.amountMinor(),
                    "KES",
                    null,
                    null,
                    null
            );
        }
        if (!"USD".equals(sourceCurrency)) {
            throw new IllegalArgumentException("Paystack conversion supports only USD or KES booking handoffs");
        }
        if (submittedQuote != null
            && (!"frankfurter".equalsIgnoreCase(submittedQuote.provider())
                || !"USD".equals(submittedQuote.baseCurrency())
                || !"KES".equals(submittedQuote.quoteCurrency())
            || submittedQuote.sourceAmountMinor() != handoff.amountMinor())) {
            throw new AccessDeniedException("The supplied FX quote does not match the booking handoff");
        }

        FxRateQuote trusted = fxRateClient.latest("USD", "KES");
        if (submittedQuote != null
            && (!trusted.date().equals(submittedQuote.rateDate())
            || trusted.rate().compareTo(submittedQuote.rate()) != 0)) {
            throw new AccessDeniedException("The supplied FX quote is not the current trusted USD/KES quote");
        }
        long convertedAmountMinor = BigDecimal.valueOf(handoff.amountMinor())
                .multiply(trusted.rate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (submittedQuote != null && convertedAmountMinor != submittedQuote.convertedAmountMinor()) {
            throw new AccessDeniedException("The converted KES amount does not match the trusted FX quote");
        }
        return new ChargeAmount(
                convertedAmountMinor,
                "KES",
                handoff.amountMinor(),
                "USD",
                trusted.rate(),
                trusted.date(),
                trusted.provider()
        );
    }

    private record ChargeAmount(
            long amountMinor,
            String currency,
            long sourceAmountMinor,
            String sourceCurrency,
            BigDecimal fxRate,
            LocalDate fxRateDate,
            String fxProvider
    ) {
    }

    private void recordProviderTransaction(Payment payment, PaystackVerification verification) {
        ProviderTransaction transaction = new ProviderTransaction();
        transaction.setPaymentId(payment.getId());
        transaction.setProvider(PAYSTACK);
        transaction.setProviderReference(payment.getProviderReference());
        transaction.setProviderTransactionId(verification.providerTransactionId());
        transaction.setStatus(verification.status());
        transaction.setGatewayResponse(verification.gatewayResponse());
        transaction.setAmountMinor(verification.amountMinor());
        transaction.setCurrency(verification.currency());
        transaction.setRawPayload(verification.rawPayload());
        providerTransactionRepository.save(transaction);
    }

    private void applyVerifiedStatus(Payment payment, PaystackVerification verification) {
        boolean correlationMatches = payment.getProviderReference().equals(verification.reference())
                && payment.getAmountMinor() == verification.amountMinor()
                && payment.getCurrency().equalsIgnoreCase(verification.currency());
        String status = verification.status() == null
                ? ""
                : verification.status().toLowerCase(Locale.ROOT);
        if (!correlationMatches) {
            payment.setStatus(PaymentStatus.FAILED);
            emitPaymentEvent(payment, "payment.verification_mismatch");
        }
        else if ("success".equals(status)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(java.time.Instant.now());
            emitPaymentEvent(payment, "payment.succeeded");
        }
        else if (status.equals("failed") || status.equals("abandoned") || status.equals("cancelled")) {
            payment.setStatus(PaymentStatus.FAILED);
            emitPaymentEvent(payment, "payment.failed");
        }
    }

    private void emitPaymentEvent(Payment payment, String eventType) {
        outboxEventRepository.save(PaymentOutboxEvent.of(
                payment.getId(),
                eventType,
                json(Map.of(
                        "paymentId", payment.getId().toString(),
                        "bookingId", payment.getBookingId().toString(),
                        "conversationId", payment.getConversationId(),
                        "userId", payment.getUserId().toString(),
                        "providerReference", payment.getProviderReference()
                ))
        ));
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception exception) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private MpesaStkPaymentResponse mpesaResponse(
            Payment payment,
            String merchantRequestId,
            String responseCode,
            String responseDescription,
            String customerMessage,
            boolean idempotentReplay
    ) {
        return new MpesaStkPaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getBookingId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getIdempotencyKey(),
                payment.getPaymentPurpose(),
                merchantRequestId,
                payment.getAccessCode(),
                responseCode,
                responseDescription,
                customerMessage,
                payment.getStatus(),
                idempotentReplay,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 120 characters");
        }
        return normalized;
    }

    private String normalizePaymentPurpose(String... paymentPurposeCandidates) {
        String paymentPurpose = firstNonBlank(paymentPurposeCandidates);
        return paymentPurpose == null ? "Travel payment" : paymentPurpose.trim();
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.max(0, digits.length() - 4)) + digits.substring(digits.length() - 4);
    }
}
