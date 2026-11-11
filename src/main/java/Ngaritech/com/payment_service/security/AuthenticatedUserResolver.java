package Ngaritech.com.payment_service.security;

import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthenticatedUserResolver {

    AuthenticatedUser resolve(Jwt jwt);
}
