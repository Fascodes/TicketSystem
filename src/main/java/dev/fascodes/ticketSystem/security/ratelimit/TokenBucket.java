package dev.fascodes.ticketSystem.security.ratelimit;


final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastNanos;

    TokenBucket(RateLimitRule rule) {
        this.capacity = rule.capacity();
        this.refillPerNano = rule.capacity() / (double) rule.refillPeriod().toNanos();
        this.tokens = rule.capacity();
        this.lastNanos = System.nanoTime();
    }

    synchronized long tryConsume() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastNanos) * refillPerNano);
        lastNanos = now;

        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        double nanosToWait = (1 - tokens) / refillPerNano;
        return Math.max(1, (long) Math.ceil(nanosToWait / 1_000_000_000d));
    }
}
