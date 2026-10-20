package com.constructflow.security;

import com.constructflow.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Defense in depth for endpoints authenticated by the refresh cookie. {@code SameSite=Strict}
 * already stops browsers sending the cookie on cross-site requests; this also rejects any request
 * whose {@code Origin} header names a site we do not serve. Requests without an Origin header
 * (non-browser clients such as curl or tests) carry no ambient browser credentials and are allowed.
 */
@Component
public class OriginValidator {

    private final List<String> allowedOrigins;

    public OriginValidator(@Value("${app.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public void assertAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new ForbiddenException("ORIGIN_NOT_ALLOWED", "Request origin is not allowed.");
        }
    }
}
