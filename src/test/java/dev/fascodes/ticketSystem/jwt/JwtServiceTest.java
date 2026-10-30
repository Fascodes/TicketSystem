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

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
        // atakujący zna kid, ale nie ma naszego klucza prywatnego
        JwtService attacker = new JwtService(
                TestKeys.ring(TestKeys.ACTIVE_KID, TestKeys.OTHER, Map.of(TestKeys.ACTIVE_KID, TestKeys.OTHER)),
                TestKeys.props("https://test.local", Duration.ofMinutes(10)), Clock.systemUTC());
        String t = attacker.generateAccessToken(userId, "a@b.c", List.of("ROLE_ADMIN"));
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerIsRejected() {
        JwtService other = new JwtService(TestKeys.ring(),
                TestKeys.props("https://evil.example", Duration.ofMinutes(10)), Clock.systemUTC());
        String t = other.generateAccessToken(userId, "a@b.c", List.of());
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    // ---------- Rotation after kid ----------

    private static JwtService signingWith(String kid, java.security.KeyPair pair, Map<String, java.security.KeyPair> verification) {
        return new JwtService(TestKeys.ring(kid, pair, verification),
                TestKeys.props("https://test.local", Duration.ofMinutes(10)), Clock.systemUTC());
    }

    @Test
    void tokenSignedWithRetiredKeyIsStillAcceptedAfterRotation() {
        JwtService before = signingWith(TestKeys.RETIRED_KID, TestKeys.RETIRED,
                Map.of(TestKeys.RETIRED_KID, TestKeys.RETIRED));
        String oldToken = before.generateAccessToken(userId, "a@b.c", List.of());

        assertThat(jwt.parse(oldToken).getSubject()).isEqualTo(userId.toString());
        String newHeader = new String(Base64.getUrlDecoder().decode(token().split("\\.")[0]), UTF_8);
        assertThat(newHeader).contains("\"kid\":\"" + TestKeys.ACTIVE_KID + "\"");
    }

    @Test
    void tokenSignedWithRemovedKeyIsRejected() {
        String oldToken = jwt.generateAccessToken(userId, "a@b.c", List.of()); 

        JwtService afterCleanup = signingWith(TestKeys.RETIRED_KID, TestKeys.RETIRED,
                Map.of(TestKeys.RETIRED_KID, TestKeys.RETIRED));
        assertThatThrownBy(() -> afterCleanup.parse(oldToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void kidPointingToDifferentKeyIsRejected() {
        JwtService liar = signingWith(TestKeys.ACTIVE_KID, TestKeys.RETIRED,
                Map.of(TestKeys.ACTIVE_KID, TestKeys.RETIRED));
        String t = liar.generateAccessToken(userId, "a@b.c", List.of());
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithoutKidIsRejected() {
        String t = Jwts.builder().issuer("https://test.local").subject(userId.toString())
                .signWith(TestKeys.PAIR.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> jwt.parse(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void keyRingRejectsMismatchedActivePair() {
        assertThatThrownBy(() -> TestKeys.ring(TestKeys.ACTIVE_KID, TestKeys.PAIR,
                Map.of(TestKeys.ACTIVE_KID, TestKeys.OTHER)))
                .isInstanceOf(IllegalStateException.class);
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