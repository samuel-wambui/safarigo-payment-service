package Ngaritech.com.payment_service.MpesaIntergration.Config;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Getter
@Setter

public class MpesaConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 160)
    private String paymentPurpose;
    private String paybillNumber;  // Also used as PartyB, BusinessShortCode
    private String consumerKey;
    private String consumerSecret;
    private String passKey;
    private String transactionDesc;
    private boolean deletedFlag = false;
    private boolean suspendedFlag = false;
}
