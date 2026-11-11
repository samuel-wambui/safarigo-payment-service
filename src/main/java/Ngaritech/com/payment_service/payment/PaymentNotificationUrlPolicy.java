package Ngaritech.com.payment_service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Component
public class PaymentNotificationUrlPolicy {

    private final List<URI> allowedOrigins;
    private final String requiredPathPrefix;

    public PaymentNotificationUrlPolicy(
            @Value("${payment.notification-allowed-origins}") String allowedOrigins,
            @Value("${payment.notification-path-prefix:/webhook-waiting/}") String requiredPathPrefix
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(URI::create)
                .toList();
        this.requiredPathPrefix = requiredPathPrefix;
    }

    public String requireAllowed(String rawUrl) {
        URI candidate;
        try {
            candidate = URI.create(rawUrl == null ? "" : rawUrl.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Payment notification URL is invalid");
        }
        boolean originAllowed = allowedOrigins.stream().anyMatch(origin ->
                same(origin.getScheme(), candidate.getScheme())
                        && same(origin.getHost(), candidate.getHost())
                        && effectivePort(origin) == effectivePort(candidate)
        );
        if (!originAllowed
                || candidate.getUserInfo() != null
                || candidate.getFragment() != null
                || candidate.getPath() == null
                || !candidate.getPath().startsWith(requiredPathPrefix)) {
            throw new AccessDeniedException("Payment notification URL is not an allowed n8n resume endpoint");
        }
        return candidate.toString();
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
