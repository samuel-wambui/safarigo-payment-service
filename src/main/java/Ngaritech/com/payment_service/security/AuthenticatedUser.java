package Ngaritech.com.payment_service.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String username,
        String verifiedEmail,
        List<String> roles,
        List<String> scopes,
        String actor
) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public Optional<String> verifiedEmailOptional() {
        return Optional.ofNullable(verifiedEmail)
                .filter(email -> !email.isBlank());
    }
}
