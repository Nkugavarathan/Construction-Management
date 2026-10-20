package com.constructflow.security;

import com.constructflow.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter for login and registration attempts per client IP. Works together with the
 * per-account lockout in {@code User}: the lockout stops guessing one account's password, while this
 * limit slows one client spraying many accounts.
 *
 * <p>Limitation: state is in memory, so with several backend instances each keeps its own count.
 * A shared store (for example Redis) would be needed at that point.
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final int limitPerMinute;
    private final Clock clock;

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.limitPerMinute = properties.loginRateLimitPerMinute();
        this.clock = clock;
    }

    public void check(String clientKey) {
        long now = clock.millis();
        if (attempts.size() > MAX_TRACKED_CLIENTS) {
            attempts.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
        }
        Deque<Long> window = attempts.computeIfAbsent(clientKey, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= limitPerMinute) {
                throw new TooManyRequestsException("Too many attempts. Wait a minute and try again.");
            }
            window.addLast(now);
        }
    }

    private static boolean isStale(Deque<Long> window, long now) {
        synchronized (window) {
            return window.isEmpty() || now - window.peekLast() > WINDOW_MILLIS;
        }
    }
}
