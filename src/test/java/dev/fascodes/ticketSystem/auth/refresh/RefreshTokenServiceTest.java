package dev.fascodes.ticketSystem.auth.refresh;

import dev.fascodes.ticketSystem.auth.exception.InvalidRefreshTokenException;
import dev.fascodes.ticketSystem.auth.exception.TokenReuseException;
import dev.fascodes.ticketSystem.common.TestKeys;
import dev.fascodes.ticketSystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository,
                TestKeys.props("https://test.local", Duration.ofMinutes(10)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setEmail("jan@example.com");
        user.setEnabled(true);
    }

    private RefreshToken stored(String raw, boolean revoked, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        t.setUser(user);
        t.setTokenHash(RefreshTokenService.hash(raw));
        t.setRevoked(revoked);
        t.setExpiresAt(expiresAt);
        return t;
    }

    private void stubSave() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
    }

    @Test
    void rotateRevokesOldIssuesNewAndStoresOnlyHash() {
        RefreshToken current = stored("raw-1", false, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw-1"))).thenReturn(Optional.of(current));
        stubSave();

        var result = service.rotate("raw-1", "UA", "127.0.0.1");

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedByTokenId()).isNotNull();
        assertThat(result.newRawToken()).isNotEqualTo("raw-1");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(result.newRawToken()));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(result.newRawToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void reuseOfRevokedTokenRevokesAllActiveTokensOfUser() {
        RefreshToken reused = stored("raw-old", true, NOW.plus(Duration.ofDays(1)));
        RefreshToken sibling = stored("raw-live", false, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw-old"))).thenReturn(Optional.of(reused));
        when(repository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> service.rotate("raw-old", "UA", "127.0.0.1"))
                .isInstanceOf(TokenReuseException.class);

        assertThat(sibling.isRevoked()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejectedWithoutTriggeringReuseHandling() {
        RefreshToken expired = stored("raw-exp", false, NOW.minusSeconds(1));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw-exp"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("raw-exp", "UA", "127.0.0.1"))
                .isExactlyInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate("nope", "UA", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void disabledUserCannotRefresh() {
        user.setEnabled(false);
        RefreshToken current = stored("raw-1", false, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw-1"))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("raw-1", "UA", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
