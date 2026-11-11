package Ngaritech.com.payment_service.MpesaIntergration.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MpesaConfigService {
    @Autowired
    private MpesaConfigRepository configRepository;

    @Autowired
    private MpesaConfiguration mpesaConfiguration;

    public MpesaConfigEntity postConfig(MpesaConfigEntity config) {
        Optional<MpesaConfigEntity> existingConfig = configRepository.findByPaymentPurposeAndDeletedFlagFalse(config.getPaymentPurpose());
        if (existingConfig.isPresent()) {
            throw new IllegalArgumentException("Payment purpose already exists with a paybill number.");
        }
        return configRepository.save(config);
    }

//    public MpesaConfigEntity updateConfig(String paybillNumber, MpesaConfigEntity newConfig) {
//  Optional<MpesaConfigEntity> configs = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber);
//        if (configs.isEmpty()) {
//            throw new IllegalArgumentException("Paybill number does not exist.");
//        }
//
//        MpesaConfigEntity existingConfig = configs.get(0); // Since there's only one active config per paybill
//        existingConfig.setConsumerKey(newConfig.getConsumerKey());
//        existingConfig.setConsumerSecret(newConfig.getConsumerSecret());
//        existingConfig.setPassKey(newConfig.getPassKey());
//        existingConfig.setTransactionDesc(newConfig.getTransactionDesc());
//        return configRepository.save(existingConfig);
//    }

//    public void deleteConfig(String paybillNumber) {
//        List<MpesaConfigEntity> configs = configRepository.findByPaybillNumberAndDeletedFlagFalse(paybillNumber);
//        for (MpesaConfigEntity config : configs) {
//            config.setDeletedFlag(true);
//            configRepository.save(config);
//        }
//    }

    public List<MpesaConfigEntity> getAllActiveConfigs() {
        return configRepository.findByDeletedFlagFalse();
    }

    public String getPaybillNumber(String paymentPurpose) {
        Optional<MpesaConfigEntity> config = configRepository.findByPaymentPurposeAndDeletedFlagFalse(paymentPurpose);
        return config.map(MpesaConfigEntity::getPaybillNumber).orElse(null);
    }

    public String getConfigValue(String paymentPurpose, String key) {
        Optional<MpesaConfigEntity> config = configRepository.findByPaymentPurposeAndDeletedFlagFalse(paymentPurpose);
        if (config.isPresent()) {
            MpesaConfigEntity cfg = config.get();
            switch (key) {
                case "consumerKey":
                    return cfg.getConsumerKey();
                case "consumerSecret":
                    return cfg.getConsumerSecret();
                case "passKey":
                    return cfg.getPassKey();
                case "transactionDesc":
                    return cfg.getTransactionDesc();
                default:
                    return null;
            }
        }
        return null;
    }

    public MpesaConfigEntity getConfigByPaymentPurpose(String paymentPurpose) {
        Optional<MpesaConfigEntity> configOptional = configRepository.findByPaymentPurposeAndDeletedFlagFalse(paymentPurpose);

        // If the configuration is not found, return the default configuration
        if (configOptional.isEmpty()) {
            configOptional = configRepository.findByPaymentPurposeAndDeletedFlagFalse("default");
            if (configOptional.isEmpty()) {
                return envDefaultConfig();
            }
        }

        return configOptional.get();
    }

    private MpesaConfigEntity envDefaultConfig() {
        if (isBlank(mpesaConfiguration.getConsumerKey())
                || isBlank(mpesaConfiguration.getConsumerSecret())
                || isBlank(mpesaConfiguration.getStkPassKey())
                || isBlank(firstNonBlank(mpesaConfiguration.getStkPushShortCode(), mpesaConfiguration.getShortCode()))) {
            throw new IllegalStateException("Default M-Pesa configuration is missing. Configure a default DB row or set MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_STK_PASS_KEY and MPESA_STK_PUSH_SHORT_CODE.");
        }

        MpesaConfigEntity config = new MpesaConfigEntity();
        config.setPaymentPurpose("default");
        config.setPaybillNumber(firstNonBlank(mpesaConfiguration.getStkPushShortCode(), mpesaConfiguration.getShortCode()));
        config.setConsumerKey(mpesaConfiguration.getConsumerKey());
        config.setConsumerSecret(mpesaConfiguration.getConsumerSecret());
        config.setPassKey(mpesaConfiguration.getStkPassKey());
        config.setTransactionDesc("Payment Service M-Pesa STK Push");
        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

}
