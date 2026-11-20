package com.returnright.core.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single injected clock (CLAUDE.md time rules). Delegates to the system UTC clock unless pinned.
 * Pinning is used only by tests and by the seeder (historical returns); production never pins it.
 */
public final class AppClock extends Clock {

    private final Clock system = Clock.systemUTC();
    private final AtomicReference<Instant> pinned = new AtomicReference<>();

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("AppClock is always UTC");
    }

    @Override
    public Instant instant() {
        Instant p = pinned.get();
        return p != null ? p : system.instant();
    }

    public void pin(Instant at) {
        pinned.set(at);
    }

    public void advance(Duration d) {
        pinned.updateAndGet(p -> (p == null ? system.instant() : p).plus(d));
    }

    public void unpin() {
        pinned.set(null);
    }
}
