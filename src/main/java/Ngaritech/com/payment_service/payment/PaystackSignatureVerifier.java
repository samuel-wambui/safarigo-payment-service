package Ngaritech.com.payment_service.payment;

import Ngaritech.com.payment_service.paystack.PaystackProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaystackSignatureVerifier {

    private final PaystackProperties paystackProperties;

    public boolean isValid(String timestampedSignature, String legacySignature, String payload) {
        if (StringUtils.hasText(timestampedSignature)) {
            return isValidTimestamped(timestampedSignature, payload);
        }
        return isValidLegacy(legacySignature, payload);
    }

    private boolean isValidTimestamped(String header, String payload) {
        if (!StringUtils.hasText(paystackProperties.webhookSigningSecret())) {
            return false;
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                }
                catch (NumberFormatException ignored) {
                    return false;
                }
            }
            else if ("v1".equals(pair[0])) {
                String[] keyedSignature = pair[1].split(":", 2);
                if (keyedSignature.length == 2) {
                    signatures.add(keyedSignature[1]);
                }
            }
        }
        if (timestamp == null || signatures.isEmpty()
                || Math.abs(Instant.now().getEpochSecond() - timestamp) > 300) {
            return false;
        }
        String expected = hmacSha512(
                paystackProperties.webhookSigningSecret(),
                timestamp + "." + (payload == null ? "" : payload)
        );
        return signatures.stream().anyMatch(signature -> constantTimeHexEquals(expected, signature));
    }

    private boolean isValidLegacy(String signature, String payload) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(paystackProperties.secretKey())) {
            return false;
        }
        String expected = hmacSha512(
                paystackProperties.secretKey(),
                payload == null ? "" : payload
        );
        return constantTimeHexEquals(expected, signature);
    }

    private boolean constantTimeHexEquals(String expected, String supplied) {
        String normalized = supplied == null ? "" : supplied.trim().toLowerCase();
        if (!normalized.matches("^[0-9a-f]{128}$")) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalized.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacSha512(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not verify Paystack signature", exception);
        }
    }
}
