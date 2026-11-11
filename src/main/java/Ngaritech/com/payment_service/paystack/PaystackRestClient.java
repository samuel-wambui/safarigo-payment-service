package Ngaritech.com.payment_service.paystack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class PaystackRestClient implements PaystackClient {

    private final RestClient.Builder restClientBuilder;
    private final PaystackProperties paystackProperties;

    @Override
    public PaystackInitialization initialize(PaystackInitializeRequest request) {
        if (!StringUtils.hasText(paystackProperties.secretKey())) {
            throw new PaystackException("Paystack secret key is not configured");
        }

        try {
            JsonNode response = client()
                    .post()
                    .uri("/transaction/initialize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("status").asBoolean(false)) {
                throw new PaystackException("Paystack did not initialize the transaction");
            }

            JsonNode data = response.path("data");
            return new PaystackInitialization(
                    data.path("authorization_url").asText(),
                    data.path("access_code").asText(),
                    data.path("reference").asText()
            );
        }
        catch (PaystackException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new PaystackException("Could not initialize Paystack transaction", exception);
        }
    }

    @Override
    public PaystackVerification verify(String reference) {
        if (!StringUtils.hasText(paystackProperties.secretKey())) {
            throw new PaystackException("Paystack secret key is not configured");
        }

        try {
            JsonNode response = client()
                    .get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("status").asBoolean(false)) {
                throw new PaystackException("Paystack did not verify the transaction");
            }

            JsonNode data = response.path("data");
            return new PaystackVerification(
                    data.path("id").asText(null),
                    data.path("reference").asText(null),
                    data.path("status").asText(null),
                    data.path("amount").asLong(-1),
                    data.path("currency").asText(null),
                    data.path("gateway_response").asText(null),
                    response.toString()
            );
        }
        catch (PaystackException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new PaystackException("Could not verify Paystack transaction", exception);
        }
    }

    private RestClient client() {
        return restClientBuilder
                .baseUrl(paystackProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + paystackProperties.secretKey())
                .build();
    }
}
