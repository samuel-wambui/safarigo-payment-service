package Ngaritech.com.payment_service.MpesaIntergration.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConfigService {

    @Autowired
    private MpesaConfigRepository configRepository;

    /**
     * Create a new Mpesa configuration.
     * If the payment purpose already exists for the paybill number, throw an exception.
     *
     * @param config the configuration details.
     * @return the created configuration.
     */
    public MpesaConfigEntity createMpesaConfig(MpesaConfigDto config) {
        if (configRepository.existsByPaymentPurposeAndPaybillNumberAndDeletedFlagFalse(config.getPaymentPurpose(), config.getPaybillNumber())) {
            throw new IllegalArgumentException("Payment purpose already exists for this paybill number.");

        }
        MpesaConfigEntity entity = new MpesaConfigEntity();
        if(config.getPaybillNumber() != null && !config.getPaybillNumber().isEmpty()) {
            entity.setPaybillNumber(config.getPaybillNumber());

        }

        if (config.getConsumerKey() != null && !config.getConsumerKey().isEmpty()) {
            entity.setConsumerKey(config.getConsumerKey());
        }
        if (config.getConsumerSecret() != null && !config.getConsumerSecret().isEmpty()) {
            entity.setConsumerSecret(config.getConsumerSecret());
        }
        if (config.getPassKey() != null && !config.getPassKey().isEmpty()) {
            entity.setPassKey(config.getPassKey());
        }
        if (config.getTransactionDesc() != null && !config.getTransactionDesc().isEmpty()) {
            entity.setTransactionDesc(config.getTransactionDesc());
        }
        if (config.getPaymentPurpose() != null && !config.getPaymentPurpose().isEmpty()) {
            entity.setPaymentPurpose(config.getPaymentPurpose());
        }
        return configRepository.save(entity);
    }

    /**
     * Update an existing Mpesa configuration by paybill number.
     *
     * @param paybillNumber the paybill number to identify the configuration.
     * @param updatedConfig the new configuration details.
     * @return the updated configuration.
     */
    public MpesaConfigEntity updateMpesaConfig(String paybillNumber, MpesaConfigDto updatedConfig) {
        MpesaConfigEntity existingConfig = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found for paybill number: " + paybillNumber));

        // Update the existing configuration with the new details
        if (updatedConfig.getConsumerKey() != null && !updatedConfig.getConsumerKey().isEmpty()) {
            existingConfig.setConsumerKey(updatedConfig.getConsumerKey());
        }
        if (updatedConfig.getConsumerSecret() != null && !updatedConfig.getConsumerSecret().isEmpty()) {
            existingConfig.setConsumerSecret(updatedConfig.getConsumerSecret());
        }
        if (updatedConfig.getPassKey() != null && !updatedConfig.getPassKey().isEmpty()) {
            existingConfig.setPassKey(updatedConfig.getPassKey());
        }
        if (updatedConfig.getTransactionDesc() != null && !updatedConfig.getTransactionDesc().isEmpty()) {
            existingConfig.setTransactionDesc(updatedConfig.getTransactionDesc());
        }
        if (updatedConfig.getPaymentPurpose() != null && !updatedConfig.getPaymentPurpose().isEmpty()) {
            existingConfig.setPaymentPurpose(updatedConfig.getPaymentPurpose());
        }

        // Add more fields as necessary

        return configRepository.save(existingConfig);
    }

    /**
     * Soft delete a Mpesa configuration by paybill number.
     *
     * @param paybillNumber the paybill number to identify the configuration.
     */

    public MpesaConfigEntity deleteMpesaConfig(String paybillNumber) {
        MpesaConfigEntity existingConfig = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found for paybill number: " + paybillNumber));

        // Mark the configuration as deleted
        existingConfig.setDeletedFlag(true);
        return configRepository.save(existingConfig);
    }

    public MpesaConfigEntity suspendMpesaConfig(String paybillNumber) {
        MpesaConfigEntity existingConfig = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found for paybill number: " + paybillNumber));

        // Mark the configuration as suspended
        existingConfig.setSuspendedFlag(true);
        return configRepository.save(existingConfig);
    }
    public MpesaConfigEntity unSuspendMpesaConfig(String paybillNumber) {
        MpesaConfigEntity existingConfig = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found for paybill number: " + paybillNumber));

        // Mark the configuration as suspended
        existingConfig.setSuspendedFlag(false);
        return configRepository.save(existingConfig);
    }


    /**
     * Get a Mpesa configuration by payment purpose.
     * If the configuration for the payment purpose is not found, return the default configuration.
     *
     * @param paymentPurpose the payment purpose to identify the configuration.
     * @return the Mpesa configuration.
     */
    public MpesaConfigEntity getConfigByPaymentPurpose(String paymentPurpose) {
        Optional<MpesaConfigEntity> configOptional = configRepository.findByPaymentPurposeAndDeletedFlagFalse(paymentPurpose);

        if (configOptional.isEmpty()) {
            configOptional = configRepository.findByPaymentPurposeAndDeletedFlagFalse("default");
            if (configOptional.isEmpty()) {
                throw new IllegalStateException("Default configuration not found. Please ensure a default configuration exists.");
            }
        }

        return configOptional.get();
    }

    /**
     * Get all active (not deleted) Mpesa configurations.
     *
     * @return the list of active Mpesa configurations.
     */
    public List<MpesaConfigEntity> getAllConfigs() {
        return configRepository.findAllByDeletedFlagFalse();
    }
}
