package Ngaritech.com.payment_service.security;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "payment.sandbox-api-key")
public class SandboxApiKeyProperties {

    private boolean enabled = true;
    private String headerName = "X-Payment-Service-Key";
    private String key;
    private String keySha256;
    private String clientId = "sandbox-integrator";
    private List<String> authorities = List.of(
            "ROLE_SERVICE",
            "SCOPE_service:sandbox",
            "SCOPE_payment:read",
            "SCOPE_payment:create",
            "SCOPE_sandbox:payment:create"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKeySha256() {
        return keySha256;
    }

    public void setKeySha256(String keySha256) {
        this.keySha256 = keySha256;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }
}
