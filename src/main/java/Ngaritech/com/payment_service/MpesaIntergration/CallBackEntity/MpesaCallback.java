package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;

import lombok.*;

import jakarta.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "mpesa_callbacks")
public class MpesaCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_request_id", nullable = false)
    private String merchantRequestID;

    @Column(name = "checkout_request_id", nullable = false)
    private String checkoutRequestID;

    @Column(name = "result_code", nullable = false)
    private int resultCode;

    @Column(name = "result_desc", nullable = false)
    private String resultDesc;

    @OneToOne(mappedBy = "mpesaCallback", cascade = CascadeType.ALL, orphanRemoval = true)
    private CallbackMetadata callbackMetadata;
}