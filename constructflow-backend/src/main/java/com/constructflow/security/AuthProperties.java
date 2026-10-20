package com.constructflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration refreshFamilyTtl,
        boolean cookieSecure,
        int maxFailedLogins,
        Duration lockoutDuration,
        int loginRateLimitPerMinute) {
}
