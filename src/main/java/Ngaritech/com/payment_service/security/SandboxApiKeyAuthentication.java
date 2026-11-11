package Ngaritech.com.payment_service.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class SandboxApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String clientId;

    public SandboxApiKeyAuthentication(String clientId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.clientId = clientId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    @Override
    public String getName() {
        return clientId;
    }
}
