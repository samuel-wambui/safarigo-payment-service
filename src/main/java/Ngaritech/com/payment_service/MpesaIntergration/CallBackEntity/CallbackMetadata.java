package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;
import lombok.*;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "callback_metadata")
public class CallbackMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "callbackMetadata", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CallbackMetadataItem> items;

    @OneToOne
    @JoinColumn(name = "mpesa_callback_id")
    private MpesaCallback mpesaCallback;
}