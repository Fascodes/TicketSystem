package dev.fascodes.ticketSystem.auth.service;

import dev.fascodes.ticketSystem.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final Clock clock;

    public LoginAttemptService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        userRepository.findByEmailForUpdate(email).ifPresent(user -> {
            Instant now = clock.instant();
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                return;
            }
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedLoginAttempts(0);
                log.warn("Account locked until {} (userId={})", user.getLockedUntil(), user.getId());
            } else {
                user.setFailedLoginAttempts(attempts);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(clock.instant());
        });
    }
}