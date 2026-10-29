package dev.fascodes.ticketSystem.jwt;

import dev.fascodes.ticketSystem.auth.jwt.JwtService;
import dev.fascodes.ticketSystem.common.MutableClock;
import dev.fascodes.ticketSystem.common.TestKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwt = TestKeys.jwtService(Duration.ofMinutes(10));
    private final UUID userId = UUID.randomUUID();

    private String token() {
        return jwt.generateAccessToken(userId, "jan@example.com", List.of("ROLE_USER"));
    }

    @Test
    void roundTripPreservesClaims() {
        Claims c = jwt.parse(token());
        assertThat(c.getSubject()).isEqualTo(userId.toString());
        assertThat(c.get("email", String.class)).isEqualTo("jan@example.com");
        assertThat(c.get("roles", List.class)).containsExactly("ROLE_USER");
        assertThat(c.getId()).isNotBlank();
    }

    @Test
    void headerHasRs256AndKid() {
        String header = new String(Base64.getUrlDecoder().decode(token().split("\\.")[0]), UTF_8);
        assertThat(header).contains("\"alg\":\"RS256\"").contains("\"kid\":\"test-key\"");
    }

    @Test
    void tokenIsValidWithinClockSkewAndRejectedAfter() {
        MutableClock clock = new MutableClock();
        JwtService service = TestKeys.jwtService(Duration.ofMinutes(10), clock);
        String t = service.generateAccessToken(userId, "a@b.c", List.of());

        clock.advance(Duration.ofMinutes(10).plusSeconds(29)); // exp + 29s mieści się w 30s skew
        assertThat(service.parse(t).getSubject()).isEqualTo(userId.toString());

        clock.advance(Duration.ofSeconds(2));
        assertThatThrownBy(() -> service.parse(t)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] p = token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(p[1]), UTF_8)
                .replace("ROLE_USER", "ROLE_ADMIN");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(UTF_8)) + "." + p[2];
        assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithForeignKeyIsRejected() {
        JwtService attacker = new JwtService((RSAPrivateKey) TestKeys.OTHER.getPrivate(),
                (RSAPublicKey) TestKeys.OTHER.getPublic(),
                TestKeys.props("https://test.local", Duration.ofMinutes(10)), Clock.systemUTC());
        String t = attacker.generateAccessToken(userId, "a@b.c", List.of("ROLE_ADMIN"));
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerIsRejected() {
        JwtService other = new JwtService((RSAPrivateKey) TestKeys.PAIR.getPrivate(),
                (RSAPublicKey) TestKeys.PAIR.getPublic(),
                TestKeys.props("https://evil.example", Duration.ofMinutes(10)), Clock.systemUTC());
        String t = other.generateAccessToken(userId, "a@b.c", List.of());
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void algorithmNoneIsRejected() {
        String unsigned = Jwts.builder().issuer("https://test.local").subject(userId.toString()).compact();
        assertThatThrownBy(() -> jwt.parse(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void algorithmConfusionHs256WithPublicKeyIsRejected() {
        String t = Jwts.builder().issuer("https://test.local").subject(userId.toString())
                .signWith(Keys.hmacShaKeyFor(TestKeys.PAIR.getPublic().getEncoded()), Jwts.SIG.HS256)
                .compact();
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }
}