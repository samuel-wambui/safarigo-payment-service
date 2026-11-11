package Ngaritech.com.payment_service.MpesaIntergration.Config;

public record MpesaConfigResponse(
        Long id,
        String paymentPurpose,
        String paybillNumber,
        String consumerKeyStatus,
        String consumerSecretStatus,
        String passKeyStatus,
        String transactionDesc,
        boolean deleted,
        boolean suspended
) {

    static MpesaConfigResponse from(MpesaConfigEntity entity) {
        return new MpesaConfigResponse(
                entity.getId(),
                entity.getPaymentPurpose(),
                entity.getPaybillNumber(),
                status(entity.getConsumerKey()),
                status(entity.getConsumerSecret()),
                status(entity.getPassKey()),
                entity.getTransactionDesc(),
                entity.isDeletedFlag(),
                entity.isSuspendedFlag()
        );
    }

    private static String status(String value) {
        return value == null || value.isBlank() ? "MISSING" : "SET";
    }
}
