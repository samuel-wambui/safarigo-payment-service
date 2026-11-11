package Ngaritech.com.payment_service.MpesaIntergration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaCallerService {

    private final RestClient.Builder restClientBuilder;

    @Value("${transaction.mpesa.mpesaexpress.requesturl:}")
    private String mpesaExpressRequestUrl;

    public MpesaExpressResponse initiateMpesaexpress(MpesaExpressRequest request) {
        if (!StringUtils.hasText(mpesaExpressRequestUrl)) {
            throw new IllegalStateException("M-Pesa express request URL is not configured");
        }

        log.info("Calling configured M-Pesa express endpoint");
        return restClientBuilder.build()
                .post()
                .uri(mpesaExpressRequestUrl)
                .body(request)
                .retrieve()
                .body(MpesaExpressResponse.class);
    }

    public record MpesaExpressRequest(
            String phoneNumber,
            Double amount,
            String accountReference,
            String transactionDescription
    ) {
    }

    public record MpesaExpressResponse(
            String merchantRequestID,
            String checkoutRequestID,
            String responseCode,
            String responseDescription,
            String customerMessage
    ) {
    }
}
