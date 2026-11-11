package Ngaritech.com.payment_service.security;

import Ngaritech.com.payment_service.common.ApiCode;
import Ngaritech.com.payment_service.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class SandboxApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final SandboxApiKeyProperties properties;
    private final ObjectMapper objectMapper;

    public SandboxApiKeyAuthenticationFilter(SandboxApiKeyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String presentedKey = request.getHeader(properties.getHeaderName());
        if (!properties.isEnabled() || !StringUtils.hasText(presentedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!matches(presentedKey.trim())) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
                    "Authentication is required",
                    ApiCode.UNAUTHORIZED,
                    null
            ));
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SandboxApiKeyAuthentication authentication = new SandboxApiKeyAuthentication(
                    properties.getClientId(),
                    properties.getAuthorities().stream()
                            .filter(StringUtils::hasText)
                            .map(SimpleGrantedAuthority::new)
                            .toList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String presentedKey) {
        String expectedHash = properties.getKeySha256();
        if (StringUtils.hasText(expectedHash)) {
            return constantTimeEquals(sha256Hex(presentedKey), expectedHash.trim().toLowerCase());
        }

        String expectedKey = properties.getKey();
        return StringUtils.hasText(expectedKey) && constantTimeEquals(presentedKey, expectedKey.trim());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
