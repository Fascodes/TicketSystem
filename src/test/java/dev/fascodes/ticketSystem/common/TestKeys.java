package dev.fascodes.ticketSystem.common;

import dev.fascodes.ticketSystem.auth.jwt.JwtKeyRing;
import dev.fascodes.ticketSystem.auth.jwt.JwtProperties;
import dev.fascodes.ticketSystem.auth.jwt.JwtService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public final class TestKeys {

    public static final String ACTIVE_KID = "test-key";
    public static final String RETIRED_KID = "retired-key";

    public static final KeyPair PAIR = generate();
    public static final KeyPair RETIRED = generate();
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
        return new JwtProperties(null, ACTIVE_KID, issuer, accessTtl, Duration.ofDays(14));
    }

    public static JwtKeyRing ring(String activeKid, KeyPair active, Map<String, KeyPair> verification) {
        var publics = new java.util.HashMap<String, RSAPublicKey>();
        verification.forEach((kid, pair) -> publics.put(kid, (RSAPublicKey) pair.getPublic()));
        return new JwtKeyRing(activeKid, (RSAPrivateKey) active.getPrivate(), publics);
    }

    public static JwtKeyRing ring() {
        return ring(ACTIVE_KID, PAIR, Map.of(ACTIVE_KID, PAIR, RETIRED_KID, RETIRED));
    }

    public static JwtService jwtService(Duration accessTtl) {
        return jwtService(accessTtl, Clock.systemUTC());
    }

    public static JwtService jwtService(Duration accessTtl, Clock clock) {
        return new JwtService(ring(), props("https://test.local", accessTtl), clock);
    }

    public static Path writeKeysDir() {
        try {
            Path dir = Files.createTempDirectory("jwt-keys");
            writePem(dir.resolve(ACTIVE_KID + ".private.pem"), "PRIVATE KEY", PAIR.getPrivate().getEncoded());
            writePem(dir.resolve(ACTIVE_KID + ".public.pem"), "PUBLIC KEY", PAIR.getPublic().getEncoded());
            writePem(dir.resolve(RETIRED_KID + ".public.pem"), "PUBLIC KEY", RETIRED.getPublic().getEncoded());
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writePem(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}
