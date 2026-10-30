package dev.fascodes.ticketSystem.security.ratelimit;

import java.time.Duration;

public record RateLimitRule(int capacity, Duration refillPeriod) {}
