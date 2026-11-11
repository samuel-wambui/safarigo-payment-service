package Ngaritech.com.payment_service.MpesaIntergration.CallBackEntity;

import lombok.*;

import jakarta.persistence.*;

@Entity
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "callback_metadata_items")
public class CallbackMetadataItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "metadata_value", nullable = false)
    private String value;

    @ManyToOne
    @JoinColumn(name = "callback_metadata_id")
    private CallbackMetadata callbackMetadata;
}
