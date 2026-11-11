package Ngaritech.com.payment_service.MpesaIntergration.Config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MpesaConfigDto {
    private String paymentPurpose;
    private String paybillNumber;  // Also used as PartyB, BusinessShortCode
    private String consumerKey;
    private String consumerSecret;
    private String passKey;
    private String transactionDesc;
}
