package Ngaritech.com.payment_service.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RestFlightAgentHandoffClient implements FlightAgentHandoffClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${flight-agent.base-url}")
    private String baseUrl;

    @Value("${flight-agent.internal-secret}")
    private String internalSecret;

    @Override
    public FlightAgentPaymentHandoff validate(
            UUID principalUserId,
            String conversationId,
            UUID bookingId
    ) {
        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("Flight-agent internal secret is not configured");
        }
        FlightAgentPaymentHandoff handoff = restClientBuilder
                .baseUrl(baseUrl)
                .build()
                .post()
                .uri("/api/v1/flight-agent/payment-handoff/validate")
                .header("X-SafariGo-Webhook-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "principalUserId", principalUserId.toString(),
                        "conversationId", conversationId,
                        "bookingId", bookingId.toString()
                ))
                .retrieve()
                .body(FlightAgentPaymentHandoff.class);
        if (handoff == null) {
            throw new IllegalStateException("Flight agent returned an empty payment handoff");
        }
        return handoff;
    }
}
