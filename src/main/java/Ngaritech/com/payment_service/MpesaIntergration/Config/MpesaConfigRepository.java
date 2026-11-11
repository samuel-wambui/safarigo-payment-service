package Ngaritech.com.payment_service.MpesaIntergration.Config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MpesaConfigRepository extends JpaRepository<MpesaConfigEntity, Long> {
//    List<MpesaConfigEntity> findByPaybillNumberAndDeletedFlagFalse(String paybillNumber);
    List<MpesaConfigEntity> findByDeletedFlagFalse();



    /**
     * Check if a configuration exists by payment purpose and not marked as deleted.
     *
     * @param paymentPurpose the payment purpose.
     * @return true if exists, otherwise false.
     */
    boolean existsByPaymentPurposeAndPaybillNumberAndDeletedFlagFalse(String paymentPurpose, String paybillNumber);

    /**
     * Find a configuration by paybill number and not marked as deleted.
     *
     * @param paybillNumber the paybill number.
     * @return an optional containing the configuration if found.
     */
    Optional<MpesaConfigEntity> findByPaybillNumberAndDeletedFlagFalse(String paybillNumber);

    /**
     * Find a configuration by payment purpose and not marked as deleted.
     *
     * @param paymentPurpose the payment purpose.
     * @return an optional containing the configuration if found.
     */
    Optional<MpesaConfigEntity> findByPaymentPurposeAndDeletedFlagFalse(String paymentPurpose);

    /**
     * Find all configurations that are not marked as deleted.
     *
     * @return the list of active configurations.
     */
    List<MpesaConfigEntity> findAllByDeletedFlagFalse();
}
