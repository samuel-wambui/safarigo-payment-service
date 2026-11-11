package Ngaritech.com.payment_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticatedUserResolver implements AuthenticatedUserResolver {

    @Value("${payment.security.claims.user-id:user_id}")
    private String userIdClaim;

    @Value("${payment.security.claims.username:username}")
    private String usernameClaim;

    @Value("${payment.security.claims.email:email}")
    private String emailClaim;

    @Value("${payment.security.claims.email-verified:email_verified}")
    private String emailVerifiedClaim;

    @Value("${payment.security.claims.fallback-sub-as-user-id:true}")
    private boolean fallbackSubAsUserId;

    @Override
    public AuthenticatedUser resolve(Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        String username = firstNonBlank(
                jwt.getClaimAsString(usernameClaim),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("client_id"),
                jwt.getSubject()
        );
        String email = verifiedEmail(jwt);

        return new AuthenticatedUser(
                userId,
                username,
                email,
                claimAsList(jwt.getClaim("roles")).stream()
                        .map(role -> role.toUpperCase(Locale.ROOT))
                        .toList(),
                scopes(jwt),
                jwt.getClaimAsString("client_id")
        );
    }

    private UUID resolveUserId(Jwt jwt) {
        String rawUserId = jwt.getClaimAsString(userIdClaim);
        if ((rawUserId == null || rawUserId.isBlank()) && fallbackSubAsUserId) {
            rawUserId = jwt.getSubject();
        }

        if (rawUserId == null || rawUserId.isBlank()) {
            throw new AccessDeniedException("IAM user id claim is required");
        }

        try {
            return UUID.fromString(rawUserId.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("IAM user id claim is invalid");
        }
    }

    private String verifiedEmail(Jwt jwt) {
        Object verified = jwt.getClaim(emailVerifiedClaim);
        if (!Boolean.TRUE.equals(verified)) {
            return null;
        }

        String email = jwt.getClaimAsString(emailClaim);
        return email == null || email.isBlank()
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> scopes(Jwt jwt) {
        List<String> scopeClaims = claimAsList(jwt.getClaim("scope"));
        if (!scopeClaims.isEmpty()) {
            return scopeClaims;
        }

        return claimAsList(jwt.getClaim("scp"));
    }

    private List<String> claimAsList(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        if (claim instanceof String value && !value.isBlank()) {
            return List.of(value.trim().split("\\s+"));
        }

        return List.of();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
