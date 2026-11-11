package Ngaritech.com.payment_service.paystack;

public interface PaystackClient {

    PaystackInitialization initialize(PaystackInitializeRequest request);

    PaystackVerification verify(String reference);
}
