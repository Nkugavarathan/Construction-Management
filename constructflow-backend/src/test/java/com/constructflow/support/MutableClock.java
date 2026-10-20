package com.constructflow.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move forward, to exercise expiry, grace periods and "overdue" rules without sleeping. */
public class MutableClock extends Clock {

    private final AtomicReference<Duration> offset;
    private final ZoneId zone;

    public MutableClock() {
        this(new AtomicReference<>(Duration.ZERO), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Duration> offset, ZoneId zone) {
        this.offset = offset;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        offset.updateAndGet(current -> current.plus(duration));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(offset, zone);
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset.get());
    }
}
