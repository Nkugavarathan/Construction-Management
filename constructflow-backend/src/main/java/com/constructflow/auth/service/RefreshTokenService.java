package com.constructflow.auth.service;

import com.constructflow.auth.entity.RefreshToken;
import com.constructflow.auth.repository.RefreshTokenRepository;
import com.constructflow.common.exception.UnauthorizedException;
import com.constructflow.security.AuthProperties;
import com.constructflow.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh-token rotation with reuse detection (ADR-0002).
 *
 * <ul>
 *   <li>Every successful refresh replaces the presented token with a new one in the same family.</li>
 *   <li>Presenting a token that was already replaced means two parties hold it, most likely because it
 *       was stolen, so the whole family (the login session) is revoked and both must log in again.</li>
 *   <li>Exception: a replaced token used again within {@link #REUSE_GRACE} is treated as a benign race
 *       (two browser tabs refreshing at once) and gets its own successor instead of a revocation.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    static final Duration REUSE_GRACE = Duration.ofSeconds(20);

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new login session (family). */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user, UUID organizationId, ClientInfo client) {
        Instant now = clock.instant();
        return issue(user, UUID.randomUUID(), organizationId, now.plus(properties.refreshFamilyTtl()), client, now);
    }

    /**
     * Rotates the presented token. Reuse revokes the family; that revocation must be committed even
     * though the request fails, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedRefreshToken rotate(String rawToken, UUID newOrganizationId, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByHashForUpdate(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalidSession);

        if (current.getRevokedAt() != null || !current.getFamilyExpiresAt().isAfter(now)
                || !current.getExpiresAt().isAfter(now)) {
            throw invalidSession();
        }
        if (current.getReplacedById() != null) {
            boolean withinGrace = current.getReplacedAt() != null
                    && current.getReplacedAt().plus(REUSE_GRACE).isAfter(now);
            if (!withinGrace) {
                log.warn("Refresh token reuse detected; revoking family {}", current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now);
                throw invalidSession();
            }
        }

        UUID organizationId = newOrganizationId != null ? newOrganizationId : current.getActiveOrganizationId();
        IssuedRefreshToken next = issue(current.getUser(), current.getFamilyId(), organizationId,
                current.getFamilyExpiresAt(), client, now);
        if (current.getReplacedById() == null) {
            current.markReplaced(next.token().getId(), now);
        }
        return next;
    }

    @Transactional
    public void revokeFamily(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private IssuedRefreshToken issue(User user, UUID familyId, UUID organizationId, Instant familyExpiresAt,
                                     ClientInfo client, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        if (expiresAt.isAfter(familyExpiresAt)) {
            expiresAt = familyExpiresAt;
        }
        RefreshToken token = repository.save(new RefreshToken(user, hash(raw), familyId, organizationId, expiresAt,
                familyExpiresAt, client.userAgent(), client.ipAddress()));
        return new IssuedRefreshToken(raw, token);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("SESSION_EXPIRED", "Your session has expired. Please log in again.");
    }

    public record IssuedRefreshToken(String rawValue, RefreshToken token) {
    }
}
