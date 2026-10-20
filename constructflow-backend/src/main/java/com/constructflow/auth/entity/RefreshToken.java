package com.constructflow.auth.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token in a login "family". Only the SHA-256 hash of the token is stored, so a
 * database leak does not hand out usable sessions.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private UUID activeOrganizationId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant familyExpiresAt;

    private Instant revokedAt;

    private UUID replacedById;

    private Instant replacedAt;

    private String userAgent;

    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, UUID familyId, UUID activeOrganizationId, Instant expiresAt,
                        Instant familyExpiresAt, String userAgent, String ipAddress) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.activeOrganizationId = activeOrganizationId;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && replacedById == null && expiresAt.isAfter(now) && familyExpiresAt.isAfter(now);
    }

    public void markReplaced(UUID replacementId, Instant now) {
        this.replacedById = replacementId;
        this.replacedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getActiveOrganizationId() {
        return activeOrganizationId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getReplacedAt() {
        return replacedAt;
    }
}
