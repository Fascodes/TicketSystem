package dev.fascodes.ticketSystem.auth.controller;

import dev.fascodes.ticketSystem.auth.jwt.JwtKeyRing;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

/** Public keys in format JWK Set (RFC 7517) - other services verify keys without access to private key*/
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(JwtKeyRing keys) {
        this.jwks = Map.of("keys", keys.publicKeys().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> toJwk(e.getKey(), e.getValue()))
                .toList());
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(jwks);
    }

    private static Map<String, Object> toJwk(String kid, RSAPublicKey key) {
        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", kid,
                "n", base64Url(key.getModulus()),
                "e", base64Url(key.getPublicExponent()));
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
