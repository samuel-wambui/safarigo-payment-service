package Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InternalStkPushStatusRequest {
    @JsonProperty("CheckoutRequestID")
    private String checkoutRequestID;
}
