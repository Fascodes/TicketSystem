package dev.fascodes.ticketSystem.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final JwtProperties props;

    public JwtService(RSAPrivateKey privateKey, RSAPublicKey publicKey, JwtProperties props) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.props = props;
    }

    public String generateAccessToken(UUID userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(props.keyId()).and()
                .issuer(props.issuer())
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .claim("email", email)
                .claim("roles", roles)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /** Throws JwtException when issuer or exp are not equal*/
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(props.issuer())
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
