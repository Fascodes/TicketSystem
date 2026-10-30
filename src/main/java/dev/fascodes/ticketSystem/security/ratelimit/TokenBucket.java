package dev.fascodes.ticketSystem.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private final Clock clock;
    private double tokens;
    private Instant last;

    TokenBucket(RateLimitRule rule, Clock clock) {
        this.capacity = rule.capacity();
        this.refillPerNano = rule.capacity() / (double) rule.refillPeriod().toNanos();
        this.tokens = rule.capacity();
        this.clock = clock;
        this.last = clock.instant();
    }

    synchronized long tryConsume() {
        Instant now = clock.instant();
        long elapsed = Math.max(0, Duration.between(last, now).toNanos());
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
        last = now;

        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        double nanosToWait = (1 - tokens) / refillPerNano;
        return Math.max(1, (long) Math.ceil(nanosToWait / 1_000_000_000d));
    }
}
