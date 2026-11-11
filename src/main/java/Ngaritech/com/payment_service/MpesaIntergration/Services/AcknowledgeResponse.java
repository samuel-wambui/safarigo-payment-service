package Ngaritech.com.payment_service.MpesaIntergration.Services;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class AcknowledgeResponse {

    private String message = "Accepted";
}
