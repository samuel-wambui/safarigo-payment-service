package Ngaritech.com.payment_service.fx;

public interface FxRateClient {

    FxRateQuote latest(String baseCurrency, String quoteCurrency);
}
