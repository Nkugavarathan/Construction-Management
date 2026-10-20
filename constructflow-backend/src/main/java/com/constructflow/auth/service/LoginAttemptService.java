package com.constructflow.auth.service;

import com.constructflow.security.AuthProperties;
import com.constructflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Tracks failed logins for account lockout. {@code REQUIRES_NEW} commits the failure count in its own
 * transaction: the login request itself ends with an exception, and without a separate transaction
 * the increment would be rolled back and lockout would never trigger.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, AuthProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            boolean locked = user.registerFailedLogin(properties.maxFailedLogins(),
                    clock.instant().plus(properties.lockoutDuration()));
            if (locked) {
                // Security event: logged (without the email) for monitoring. See threat model.
                log.warn("Account {} locked after {} failed logins", userId, properties.maxFailedLogins());
            }
        });
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> user.registerSuccessfulLogin(clock.instant()));
    }
}
