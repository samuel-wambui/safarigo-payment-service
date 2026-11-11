package Ngaritech.com.payment_service.MpesaIntergration.Services;

import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionRequest;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.InternalB2CTransactionRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Config.MpesaConfigEntity;
import Ngaritech.com.payment_service.MpesaIntergration.Config.MpesaConfigService;
import Ngaritech.com.payment_service.MpesaIntergration.Config.MpesaConfiguration;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.ExternalStkPushRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushStatusRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushStatusRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushStatusResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.OAUTH_Token.AccessTokenResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Register_URL.RegisterUrlRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Register_URL.RegisterUrlResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Utils.Constants;
import Ngaritech.com.payment_service.MpesaIntergration.Utils.HelperUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class DarajaApiImpl implements DarajaApi {

    private final MpesaConfiguration mpesaConfiguration;
    private final MpesaConfigService configService;
    private final RestClient.Builder restClientBuilder;

    @Override
    public AccessTokenResponse getAccessToken(MpesaConfigEntity config) {
        log.info("mpesa.oauth.requesting_token endpoint={} paymentPurpose={}",
                mpesaConfiguration.getOauthEndpoint(),
                config.getPaymentPurpose());
        String encodedCredentials = HelperUtility.toBase64String(String.format(
                "%s:%s",
                firstNonBlank(config.getConsumerKey(), mpesaConfiguration.getConsumerKey()),
                firstNonBlank(config.getConsumerSecret(), mpesaConfiguration.getConsumerSecret())
        ));

        AccessTokenResponse response = restClientBuilder.build()
                .get()
                .uri("%s?grant_type=%s".formatted(mpesaConfiguration.getOauthEndpoint(), mpesaConfiguration.getGrantType()))
                .header(Constants.AUTHORIZATION_HEADER_STRING, "%s %s".formatted(Constants.BASIC_AUTH_STRING, encodedCredentials))
                .header(Constants.CACHE_CONTROL_HEADER, Constants.CACHE_CONTROL_HEADER_VALUE)
                .retrieve()
                .body(AccessTokenResponse.class);
        log.info("mpesa.oauth.token_received endpoint={} tokenPresent={}",
                mpesaConfiguration.getOauthEndpoint(),
                response != null && response.getAccessToken() != null && !response.getAccessToken().isBlank());
        return response;
    }

    @Override
    public RegisterUrlResponse registerUrl() {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose("default");
        AccessTokenResponse accessTokenResponse = getAccessToken(config);

        RegisterUrlRequest registerUrlRequest = new RegisterUrlRequest();
        registerUrlRequest.setConfirmationURL(mpesaConfiguration.getConfirmationURL());
        registerUrlRequest.setResponseType(mpesaConfiguration.getResponseType());
        registerUrlRequest.setShortCode(firstNonBlank(config.getPaybillNumber(), mpesaConfiguration.getShortCode()));
        registerUrlRequest.setValidationURL(mpesaConfiguration.getValidationURL());

        return postWithBearer(mpesaConfiguration.getRegisterUrl(), registerUrlRequest, accessTokenResponse, RegisterUrlResponse.class);
    }

    @Override
    public C2BResponse C2BTransaction(C2BRequest c2BRequest) {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose("default");
        AccessTokenResponse accessTokenResponse = getAccessToken(config);
        if (c2BRequest.getShortCode() == null || c2BRequest.getShortCode().isBlank()) {
            c2BRequest.setShortCode(firstNonBlank(config.getPaybillNumber(), mpesaConfiguration.getShortCode()));
        }
        return postWithBearer(mpesaConfiguration.getC2BTransaction(), c2BRequest, accessTokenResponse, C2BResponse.class);
    }

    @Override
    public B2CTransactionSyncResponse b2CTransaction(InternalB2CTransactionRequest request) {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose("default");
        AccessTokenResponse accessTokenResponse = getAccessToken(config);

        B2CTransactionRequest b2CTransactionRequest = new B2CTransactionRequest();
        b2CTransactionRequest.setCommandID(request.getCommandID());
        b2CTransactionRequest.setAmount(request.getAmount());
        b2CTransactionRequest.setPartyB(formatPhoneNumber(request.getPartyB()));
        b2CTransactionRequest.setRemarks(request.getRemarks());
        b2CTransactionRequest.setOccassion(request.getOccassion());
        b2CTransactionRequest.setSecurityCredential(HelperUtility.getSecurityCredentials(mpesaConfiguration.getB2cInitiatorPassword()));
        b2CTransactionRequest.setResultURL(mpesaConfiguration.getB2cResultUrl());
        b2CTransactionRequest.setQueueTimeOutURL(mpesaConfiguration.getB2cQueueTimeoutUrl());
        b2CTransactionRequest.setInitiatorName(mpesaConfiguration.getB2cInitiatorName());
        b2CTransactionRequest.setPartyA(firstNonBlank(config.getPaybillNumber(), mpesaConfiguration.getShortCode()));

        return postWithBearer(mpesaConfiguration.getB2cTransactionEndpoint(), b2CTransactionRequest, accessTokenResponse, B2CTransactionSyncResponse.class);
    }

    @Override
    public StkPushSyncResponse stkPushTransaction(InternalStkPushRequest request) {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose(request.getPaymentPurpose());
        if (config.isSuspendedFlag()) {
            throw new IllegalStateException("M-Pesa configuration is suspended.");
        }

        String reference = firstNonBlank(request.getTransactionCode(), HelperUtility.getTransactionUniqueNumber());
        String timestamp = HelperUtility.getTransactionTimestamp();
        String paybillNumber = firstNonBlank(config.getPaybillNumber(), mpesaConfiguration.getStkPushShortCode(), mpesaConfiguration.getShortCode());
        String passKey = firstNonBlank(config.getPassKey(), mpesaConfiguration.getStkPassKey());

        ExternalStkPushRequest externalRequest = new ExternalStkPushRequest();
        externalRequest.setBusinessShortCode(paybillNumber);
        externalRequest.setPassword(HelperUtility.getStkPushPassword(paybillNumber, passKey, timestamp));
        externalRequest.setTimestamp(timestamp);
        externalRequest.setTransactionType(Constants.CUSTOMER_PAYBILL_ONLINE);
        externalRequest.setAmount(request.getTransactionAmount());
        externalRequest.setPartyA(formatPhoneNumber(request.getTransactionNumber()));
        externalRequest.setPartyB(paybillNumber);
        externalRequest.setPhoneNumber(formatPhoneNumber(request.getTransactionNumber()));
        externalRequest.setCallBackURL(mpesaConfiguration.getStkPushRequestCallbackUrl());
        externalRequest.setAccountReference(reference);
        externalRequest.setTransactionDesc(firstNonBlank(config.getTransactionDesc(), "%s Transaction".formatted(reference)));

        log.info(
                "mpesa.stk.submitting_to_daraja shortcode={} amountKes={} phone={} accountReference={} callbackUrl={} transactionDesc={}",
                paybillNumber,
                request.getTransactionAmount(),
                maskPhone(request.getTransactionNumber()),
                reference,
                mpesaConfiguration.getStkPushRequestCallbackUrl(),
                externalRequest.getTransactionDesc()
        );
        AccessTokenResponse accessTokenResponse = getAccessToken(config);
        StkPushSyncResponse response = postWithBearer(mpesaConfiguration.getStkPushRequestUrl(), externalRequest, accessTokenResponse, StkPushSyncResponse.class);
        log.info(
                "mpesa.stk.daraja_response merchantRequestId={} checkoutRequestId={} responseCode={} responseDescription={}",
                response == null ? null : response.getMerchantRequestID(),
                response == null ? null : response.getCheckoutRequestID(),
                response == null ? null : response.getResponseCode(),
                response == null ? null : response.getResponseDescription()
        );
        return response;
    }

    @Override
    public StkPushStatusResponse stkPushStatus(InternalStkPushStatusRequest request, String paymentPurpose) {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose(paymentPurpose);
        String timestamp = HelperUtility.getTransactionTimestamp();
        String paybillNumber = firstNonBlank(config.getPaybillNumber(), mpesaConfiguration.getStkPushShortCode(), mpesaConfiguration.getShortCode());
        String passKey = firstNonBlank(config.getPassKey(), mpesaConfiguration.getStkPassKey());

        StkPushStatusRequest statusRequest = new StkPushStatusRequest();
        statusRequest.setCheckoutRequestID(request.getCheckoutRequestID());
        statusRequest.setPassword(HelperUtility.getStkPushPassword(paybillNumber, passKey, timestamp));
        statusRequest.setBusinessShortCode(paybillNumber);
        statusRequest.setTimestamp(timestamp);

        return postWithBearer(mpesaConfiguration.getStkPushTransactionStatus(), statusRequest, getAccessToken(config), StkPushStatusResponse.class);
    }

    public String getfomatedPhoneNumber(String input) {
        return formatPhoneNumber(input);
    }

    private <T> T postWithBearer(String url, Object request, AccessTokenResponse accessTokenResponse, Class<T> responseType) {
        if (accessTokenResponse == null || accessTokenResponse.getAccessToken() == null || accessTokenResponse.getAccessToken().isBlank()) {
            throw new IllegalStateException("M-Pesa access token could not be resolved");
        }
        return restClientBuilder.build()
                .post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "%s %s".formatted(Constants.BEARER_AUTH_STRING, accessTokenResponse.getAccessToken()))
                .body(request)
                .retrieve()
                .body(responseType);
    }

    private String formatPhoneNumber(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "254" + digits.substring(1);
        }
        if (digits.length() >= 9) {
            return "254" + digits.substring(digits.length() - 9);
        }
        throw new IllegalArgumentException("Invalid phone number");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
