package dev.fascodes.ticketSystem.auth.refresh;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshTokenCleanup {

    private final RefreshTokenRepository repository;

    public RefreshTokenCleanup(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 30 3 * * *") // at 03:30
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
