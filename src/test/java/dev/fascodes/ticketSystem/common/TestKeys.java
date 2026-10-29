package dev.fascodes.ticketSystem.common;

import dev.fascodes.ticketSystem.auth.jwt.JwtProperties;
import dev.fascodes.ticketSystem.auth.jwt.JwtService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

public final class TestKeys {

    public static final KeyPair PAIR = generate();
    public static final KeyPair OTHER = generate();

    private static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JwtProperties props(String issuer, Duration accessTtl) {
        return new JwtProperties(null, null, "test-key", issuer, accessTtl, Duration.ofDays(14));
    }

    public static JwtService jwtService(Duration accessTtl) {
        return jwtService(accessTtl, Clock.systemUTC());
    }

    public static JwtService jwtService(Duration accessTtl, Clock clock) {
        return new JwtService((RSAPrivateKey) PAIR.getPrivate(),
                (RSAPublicKey) PAIR.getPublic(),
                props("https://test.local", accessTtl), clock);
    }
}
