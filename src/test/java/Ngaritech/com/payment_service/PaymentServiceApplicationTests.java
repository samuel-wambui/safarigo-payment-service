package Ngaritech.com.payment_service;

import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.InternalB2CTransactionRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Config.MpesaConfigEntity;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushStatusRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushStatusResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.OAUTH_Token.AccessTokenResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Register_URL.RegisterUrlResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Services.DarajaApi;
import Ngaritech.com.payment_service.payment.PaymentRepository;
import Ngaritech.com.payment_service.payment.PaymentStatus;
import Ngaritech.com.payment_service.payment.FlightAgentHandoffClient;
import Ngaritech.com.payment_service.payment.FlightAgentPaymentHandoff;
import Ngaritech.com.payment_service.outbox.PaymentOutboxEventRepository;
import Ngaritech.com.payment_service.webhook.PaymentWebhookEventRepository;
import Ngaritech.com.payment_service.provider.ProviderTransactionRepository;
import Ngaritech.com.payment_service.refund.RefundRepository;
import Ngaritech.com.payment_service.paystack.PaystackClient;
import Ngaritech.com.payment_service.paystack.PaystackInitialization;
import Ngaritech.com.payment_service.paystack.PaystackVerification;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "server.port=18082",
        "spring.datasource.url=jdbc:h2:mem:payment_service_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://iam.test",
        "payment.security.expected-audience=payment-service",
        "payment.sandbox-api-key.key-sha256=f1ca7a923229f4a27705e2dc13a5627d5c7194c92842c29e4e9002b04704361e",
        "payment.sandbox-api-key.client-id=test-sandbox-client",
        "paystack.secret-key=test-secret",
        "paystack.webhook-signing-secret=test-webhook-secret",
        "paystack.callback-url=http://localhost:8082/api/v1/payments/paystack/callback",
        "payment.notifications.enabled=false"
})
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    private static final String ISSUER = "http://iam.test";
    private static final String EXPECTED_AUDIENCE = "payment-service";
    private static final KeyPair KEY_PAIR = keyPair();
    private static final JwtEncoder JWT_ENCODER = jwtEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private ProviderTransactionRepository providerTransactionRepository;

    @Autowired
    private PaymentOutboxEventRepository outboxEventRepository;

    @Autowired
    private RefundRepository refundRepository;

    @BeforeEach
    void cleanDatabase() {
        providerTransactionRepository.deleteAll();
        webhookEventRepository.deleteAll();
        outboxEventRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void startsOnIndependentConfiguredPortAndUsesPaymentSchemaOnly() {
        assertEquals("18082", environment.getProperty("server.port"));

        List<String> tables = jdbcTemplate.queryForList(
                "select lower(table_name) from information_schema.tables where lower(table_schema) = 'public'",
                String.class
        );

        assertTrue(tables.contains("payments"));
        assertTrue(tables.contains("provider_transactions"));
        assertTrue(tables.contains("payment_webhook_events"));
        assertTrue(tables.contains("refunds"));
        assertTrue(tables.contains("payment_outbox_events"));
        assertTrue(tables.contains("mpesa_config_entity"));
        assertTrue(tables.contains("mpesa_callbacks"));
        assertTrue(tables.contains("callback_metadata"));
        assertTrue(tables.contains("callback_metadata_items"));

        assertFalse(tables.contains("users"));
        assertFalse(tables.contains("roles"));
        assertFalse(tables.contains("user_roles"));
        assertFalse(tables.contains("oauth2_registered_client"));
        assertFalse(tables.contains("oauth2_authorization"));
    }

    @Test
    void protectedRequestsWithoutIamJwtAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sandboxApiKeyCanCreateAndReadSandboxPayment() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/sandbox/payments")
                        .header("X-Payment-Service-Key", "test-sandbox-secret")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"amountMinor":125000,"currency":"KES","customerEmail":"partner@example.test","externalReference":"partner-ref-001"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        UUID paymentId = UUID.fromString(response.path("data").path("id").asText());

        assertEquals("partner-ref-001", response.path("data").path("providerReference").asText());
        assertEquals("PENDING", response.path("data").path("status").asText());
        assertEquals("test-sandbox-client", paymentRepository.findById(paymentId).orElseThrow().getCreatedByActor());

        mockMvc.perform(get("/api/v1/sandbox/payments/{paymentId}", paymentId)
                        .header("X-Payment-Service-Key", "test-sandbox-secret"))
                .andExpect(status().isOk());
    }

    @Test
    void sandboxApiKeyCanInitiateMpesaStkPaymentWithCleanResponse() throws Exception {
        String idempotencyKey = "mpesa-test-key-001";
        String responseBody = mockMvc.perform(post("/api/v1/payments/mpesa/stk")
                        .header("X-Payment-Service-Key", "test-sandbox-secret")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"amountMinor":100,"phoneNumber":"0746703511","transactionType":"default","customerEmail":"traveller@example.test","paymentPurpose":"Booking deposit"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        UUID paymentId = UUID.fromString(response.path("data").path("paymentId").asText());

        assertEquals("M-Pesa STK push sent", response.path("message").asText());
        assertEquals("MPESA", response.path("data").path("provider").asText());
        assertEquals("PENDING", response.path("data").path("status").asText());
        assertEquals("checkout-test-001", response.path("data").path("checkoutRequestId").asText());
        assertEquals(idempotencyKey, response.path("data").path("idempotencyKey").asText());
        assertEquals("Booking deposit", response.path("data").path("paymentPurpose").asText());
        assertFalse(response.path("data").path("idempotentReplay").asBoolean());
        assertEquals("checkout-test-001", paymentRepository.findById(paymentId).orElseThrow().getAccessCode());

        String replayBody = mockMvc.perform(post("/api/v1/payments/mpesa/stk")
                        .header("X-Payment-Service-Key", "test-sandbox-secret")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"amountMinor":100,"phoneNumber":"0746703511","transactionType":"default","customerEmail":"traveller@example.test","paymentPurpose":"Booking deposit"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode replay = objectMapper.readTree(replayBody);
        assertEquals(paymentId.toString(), replay.path("data").path("paymentId").asText());
        assertTrue(replay.path("data").path("idempotentReplay").asBoolean());
    }

    @Test
    void missingOrWrongSandboxApiKeyCannotUseSandboxEndpoints() throws Exception {
        String payload = """
                {"amountMinor":125000,"currency":"KES","customerEmail":"partner@example.test"}
                """;

        mockMvc.perform(post("/api/v1/sandbox/payments")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/sandbox/payments")
                        .header("X-Payment-Service-Key", "wrong-secret")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void iamIssuedJwtWithCorrectIssuerAudienceRolesAndScopesCanCreatePayment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        JsonNode response = createPayment(userId, bookingId, "");

        assertEquals(userId.toString(), response.path("data").path("userId").asText());
        assertEquals(bookingId.toString(), response.path("data").path("bookingId").asText());
        assertEquals("PENDING", response.path("data").path("status").asText());
        assertTrue(response.path("data").path("authorizationUrl").asText().startsWith("https://checkout.paystack.test/"));
    }

    @Test
    void requestBodyCannotAssignUserIdOrRoles() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID attackerUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String extraFields = """
                ,"userId":"%s","roles":["ADMIN"],"email":"attacker@example.test"
                """.formatted(attackerUserId);

        JsonNode response = createPayment(authenticatedUserId, bookingId, extraFields);

        assertEquals(authenticatedUserId.toString(), response.path("data").path("userId").asText());
    }

    @Test
    void wrongIssuerWrongAudienceExpiredAndGoogleTokensAreRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(travellerToken(userId, "https://wrong-issuer.test", List.of(EXPECTED_AUDIENCE), Duration.ofMinutes(5)))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(travellerToken(userId, ISSUER, List.of("booking-service"), Duration.ofMinutes(5)))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(travellerToken(userId, ISSUER, List.of(EXPECTED_AUDIENCE), Duration.ofMinutes(-10)))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(travellerToken(userId, "https://accounts.google.com", List.of(EXPECTED_AUDIENCE), Duration.ofMinutes(5)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void travellerCannotAccessAnotherTravellersPayment() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherTravellerId = UUID.randomUUID();
        JsonNode created = createPayment(ownerId, UUID.randomUUID(), "");
        UUID paymentId = UUID.fromString(created.path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(tokenFor(
                                otherTravellerId,
                                ISSUER,
                                List.of(EXPECTED_AUDIENCE),
                                List.of("TRAVELLER"),
                                List.of("payment:read"),
                                Duration.ofMinutes(5)
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void n8nInternalReadRequiresServiceAndPaymentReadScopes() throws Exception {
        JsonNode created = createPayment(UUID.randomUUID(), UUID.randomUUID(), "");
        UUID paymentId = UUID.fromString(created.path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/internal/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(clientToken(List.of("payment:read")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(clientToken(List.of("service:n8n", "payment:read")))))
                .andExpect(status().isOk());
    }

    @Test
    void callbackIsPublicButDoesNotConfirmPayment() throws Exception {
        JsonNode created = createPayment(UUID.randomUUID(), UUID.randomUUID(), "");
        UUID paymentId = UUID.fromString(created.path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/payments/paystack/callback"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("secure provider notification")));

        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(paymentId).orElseThrow().getStatus());
    }

    @Test
    void paystackWebhookRequiresValidSignatureAndUpdatesPaymentOnlyAfterWebhook() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode created = createPayment(userId, UUID.randomUUID(), "");
        UUID paymentId = UUID.fromString(created.path("data").path("id").asText());
        String reference = created.path("data").path("providerReference").asText();
        String bookingId = created.path("data").path("bookingId").asText();
        String conversationId = created.path("data").path("conversationId").asText();
        String payload = """
                {"event":"charge.success","data":{"id":4099260516,"status":"success","reference":"%s","gateway_response":"Approved","amount":250000,"currency":"KES","metadata":{"booking_id":"%s","iam_user_id":"%s","conversation_id":"%s"}}}
                """.formatted(reference, bookingId, userId, conversationId).trim();

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType(APPLICATION_JSON)
                        .header("x-paystack-signature", "invalid")
                        .content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType(APPLICATION_JSON)
                        .header("x-paystack-signature", paystackSignature(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findById(paymentId).orElseThrow().getStatus());
    }

    @Test
    void adminCanRequestRefundWithRefundScope() throws Exception {
        UUID adminId = UUID.randomUUID();
        JsonNode created = createPayment(UUID.randomUUID(), UUID.randomUUID(), "");
        UUID paymentId = UUID.fromString(created.path("data").path("id").asText());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refunds", paymentId)
                        .header("Authorization", bearer(tokenFor(
                                adminId,
                                ISSUER,
                                List.of(EXPECTED_AUDIENCE),
                                List.of("ADMIN"),
                                List.of("payment:refund"),
                                Duration.ofMinutes(5)
                        )))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"amountMinor":1000,"reason":"customer requested change"}
                                """))
                .andExpect(status().isCreated());
    }

    private JsonNode createPayment(UUID userId, UUID bookingId, String extraFields) throws Exception {
        String conversationId = "conversation-" + bookingId;
        String content = """
                {"bookingId":"%s","conversationId":"%s","notificationUrl":"http://127.0.0.1:5678/webhook-waiting/%s?signature=test"%s}
                """.formatted(bookingId, conversationId, bookingId, extraFields);

        String response = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(tokenFor(
                                userId,
                                ISSUER,
                                List.of(EXPECTED_AUDIENCE),
                                List.of("traveller"),
                                List.of("payment:create", "payment:read"),
                                Duration.ofMinutes(5)
                        )))
                        .header("Idempotency-Key", "booking-" + bookingId)
                        .contentType(APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private String travellerToken(UUID userId, String issuer, List<String> audience, Duration ttl) {
        return tokenFor(userId, issuer, audience, List.of("TRAVELLER"), List.of("payment:read"), ttl);
    }

    private String tokenFor(
            UUID userId,
            String issuer,
            List<String> audience,
            List<String> roles,
            List<String> scopes,
            Duration ttl
    ) {
        Instant now = Instant.now();
        Instant issuedAt = ttl.isNegative() ? now.plus(ttl).minusSeconds(60) : now;
        Instant expiresAt = ttl.isNegative() ? now.plus(ttl) : now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audience)
                .issuedAt(issuedAt)
                .notBefore(issuedAt.minusSeconds(1))
                .expiresAt(expiresAt)
                .claim("user_id", userId.toString())
                .claim("username", "samuel")
                .claim("email", "samuel@example.test")
                .claim("email_verified", true)
                .claim("roles", roles)
                .claim("scope", String.join(" ", scopes))
                .build();

        return encode(claims);
    }

    private String clientToken(List<String> scopes) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("n8n-orchestrator")
                .audience(List.of(EXPECTED_AUDIENCE))
                .issuedAt(now)
                .notBefore(now.minusSeconds(1))
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("client_id", "n8n-orchestrator")
                .claim("roles", List.of("SERVICE"))
                .claim("scope", String.join(" ", scopes))
                .build();

        return encode(claims);
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("test-key")
                .build();
        return JWT_ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String paystackSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static JwtEncoder jwtEncoder() {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                .privateKey((RSAPrivateKey) KEY_PAIR.getPrivate())
                .keyID("test-key")
                .build();
        JWKSource<SecurityContext> jwkSource = (selector, context) -> selector.select(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not create test key pair", exception);
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
            OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(EXPECTED_AUDIENCE)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "Missing required audience",
                    null
            ));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    audienceValidator
            ));
            return decoder;
        }

        @Bean
        @Primary
        PaystackClient paystackClient() {
            return new PaystackClient() {
                @Override
                public PaystackInitialization initialize(Ngaritech.com.payment_service.paystack.PaystackInitializeRequest request) {
                    return new PaystackInitialization(
                            "https://checkout.paystack.test/" + request.reference(),
                            "access_" + request.reference(),
                            request.reference()
                    );
                }

                @Override
                public PaystackVerification verify(String reference) {
                    return new PaystackVerification(
                            "4099260516",
                            reference,
                            "success",
                            250000,
                            "KES",
                            "Approved",
                            "{}"
                    );
                }
            };
        }

        @Bean
        @Primary
        FlightAgentHandoffClient flightAgentHandoffClient() {
            return (principalUserId, conversationId, bookingId) -> new FlightAgentPaymentHandoff(
                    conversationId,
                    principalUserId,
                    bookingId,
                    "INSTANT",
                    "2500.00",
                    250000,
                    "KES",
                    "off_test"
            );
        }

        @Bean
        @Primary
        DarajaApi darajaApi() {
            return new DarajaApi() {
                @Override
                public AccessTokenResponse getAccessToken(MpesaConfigEntity config) {
                    return null;
                }

                @Override
                public RegisterUrlResponse registerUrl() {
                    return null;
                }

                @Override
                public C2BResponse C2BTransaction(C2BRequest c2BRequest) {
                    return null;
                }

                @Override
                public B2CTransactionSyncResponse b2CTransaction(InternalB2CTransactionRequest internalB2CTransactionRequest) {
                    return null;
                }

                @Override
                public StkPushSyncResponse stkPushTransaction(InternalStkPushRequest internalStkPushRequest) {
                    StkPushSyncResponse response = new StkPushSyncResponse();
                    response.setMerchantRequestID("merchant-test-001");
                    response.setCheckoutRequestID("checkout-test-001");
                    response.setResponseCode("0");
                    response.setResponseDescription("Success. Request accepted for processing");
                    response.setCustomerMessage("Success. Request accepted for processing");
                    return response;
                }

                @Override
                public StkPushStatusResponse stkPushStatus(InternalStkPushStatusRequest internalStkPushStatusRequest, String type) {
                    return null;
                }
            };
        }
    }
}
