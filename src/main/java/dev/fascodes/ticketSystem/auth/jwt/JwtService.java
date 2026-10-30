package dev.fascodes.ticketSystem.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtKeyRing keys;
    private final JwtProperties props;
    private final Clock clock;

    public JwtService(JwtKeyRing keys, JwtProperties props, Clock clock) {
        this.keys = keys;
        this.props = props;
        this.clock = clock;
    }

    public String generateAccessToken(UUID userId, String email, Collection<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .header().keyId(keys.activeKid()).and()
                .issuer(props.issuer())
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .claim("email", email)
                .claim("roles", roles)
                .signWith(keys.signingKey(), Jwts.SIG.RS256)
                .compact();
    }

    /** Throws JwtException when kid is unknown, signature is invalid, issuer differs or token expired */
    public Claims parse(String token) {
        return Jwts.parser()
                .keyLocator(new KidLocator())
                .requireIssuer(props.issuer())
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }

    private class KidLocator extends LocatorAdapter<Key> {
        @Override
        protected Key locate(JwsHeader header) {
            return keys.publicKey(header.getKeyId())
                    .orElseThrow(() -> new SignatureException("Unknown or missing kid"));
        }
    }
}
