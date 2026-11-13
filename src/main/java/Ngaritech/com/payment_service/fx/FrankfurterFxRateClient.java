package Ngaritech.com.payment_service.fx;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FrankfurterFxRateClient implements FxRateClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${fx.frankfurter.base-url:https://api.frankfurter.dev}")
    private String baseUrl;

    @Override
    public FxRateQuote latest(String baseCurrency, String quoteCurrency) {
        String base = baseCurrency.trim().toUpperCase(Locale.ROOT);
        String quote = quoteCurrency.trim().toUpperCase(Locale.ROOT);
        JsonNode response = restClientBuilder
                .baseUrl(baseUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/rates")
                        .queryParam("base", base)
                        .queryParam("quotes", quote)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.isArray() || response.isEmpty()) {
            throw new IllegalStateException("Frankfurter returned an empty FX quote");
        }
        JsonNode row = response.get(0);
        String returnedBase = row.path("base").asText("").toUpperCase(Locale.ROOT);
        String returnedQuote = row.path("quote").asText("").toUpperCase(Locale.ROOT);
        BigDecimal rate = row.path("rate").decimalValue();
        if (!base.equals(returnedBase) || !quote.equals(returnedQuote) || rate.signum() <= 0) {
            throw new IllegalStateException("Frankfurter returned an invalid FX quote");
        }
        return new FxRateQuote(
                "frankfurter",
                LocalDate.parse(row.path("date").asText()),
                returnedBase,
                returnedQuote,
                rate
        );
    }
}
