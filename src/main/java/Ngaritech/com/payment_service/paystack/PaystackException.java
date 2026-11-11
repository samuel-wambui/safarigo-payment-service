package Ngaritech.com.payment_service.paystack;

public class PaystackException extends RuntimeException {

    public PaystackException(String message) {
        super(message);
    }

    public PaystackException(String message, Throwable cause) {
        super(message, cause);
    }
}
