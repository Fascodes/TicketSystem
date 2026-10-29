package dev.fascodes.ticketSystem.auth.refresh;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Component
public class RefreshTokenCleanup {

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *") // at 03:30
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }
}
