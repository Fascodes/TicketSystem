package dev.fascodes.ticketSystem.auth.refresh;

import dev.fascodes.ticketSystem.auth.exception.InvalidRefreshTokenException;
import dev.fascodes.ticketSystem.auth.exception.TokenReuseException;
import dev.fascodes.ticketSystem.auth.jwt.JwtProperties;
import dev.fascodes.ticketSystem.security.userdetails.CustomUserDetails;
import dev.fascodes.ticketSystem.user.entity.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public record Rotation(User user, String newRawToken) {}

    @Transactional
    public String issue(User user, String userAgent, String ip) {
        String raw = generateRaw();
        repository.save(build(user, raw, userAgent, ip));
        return raw;
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public Rotation rotate(String rawToken, String userAgent, String ip) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            revokeAllFor(current.getUser());
            throw new TokenReuseException();
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = current.getUser();
        CustomUserDetails details = new CustomUserDetails(user);
        if (!details.isEnabled() || !details.isAccountNonLocked()) {
            throw new InvalidRefreshTokenException();
        }

        String newRaw = generateRaw();
        RefreshToken next = repository.save(build(user, newRaw, userAgent, ip));

        current.setRevoked(true);
        current.setReplacedByTokenId(next.getId());

        return new Rotation(user, newRaw);
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    private void revokeAllFor(User user) {
        repository.findAllByUserAndRevokedFalse(user).forEach(t -> t.setRevoked(true));
    }

    private RefreshToken build(User user, String raw, String userAgent, String ip) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(props.refreshTtl()));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        token.setIpAddress(ip);
        return token;
    }

    private static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}