package Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InternalStkPushRequest {
    @JsonProperty("transactionAmount")
    private Double transactionAmount;
    @JsonProperty("user")
    private String user;
    @JsonProperty("transactionNumber")
    private String transactionNumber;
    @JsonProperty("transactionCode")
    private String transactionCode;
    @JsonProperty("transactionDate")
    private String transactionDate;
    @JsonProperty("paymentPurpose")
    private String paymentPurpose;

    @JsonProperty("transactionType")
    private String transactionType;
}
