package com.constructflow.user.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

/**
 * A global identity (one per email). What a user may do is decided by their organization
 * memberships, not by anything stored here.
 */
@Entity
@Table(name = "users")
public class User extends TimestampedEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private int failedLoginCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, boolean mustChangePassword) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.mustChangePassword = mustChangePassword;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Returns true when this failure caused the account to lock. */
    public boolean registerFailedLogin(int maxAttempts, Instant lockUntil) {
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    public void rename(String fullName) {
        this.fullName = fullName.trim();
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
