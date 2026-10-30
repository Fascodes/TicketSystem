package dev.fascodes.ticketSystem.auth.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;


@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
    private static final String PUBLIC_SUFFIX = ".public.pem";
    private static final String PRIVATE_SUFFIX = ".private.pem";
    private static final Pattern KID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final int MIN_KEY_BITS = 2048;

    @Bean
    public JwtKeyRing jwtKeyRing(JwtProperties props) {
        Path dir = props.keysDir();
        String activeKid = props.activeKeyId();
        if (activeKid == null || !KID.matcher(activeKid).matches()) {
            throw new IllegalStateException("Invalid app.jwt.active-key-id: '" + activeKid + "'");
        }
        try {
            Map<String, RSAPublicKey> publicKeys = new HashMap<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.getFileName().toString().endsWith(PUBLIC_SUFFIX)).toList()) {
                    String name = file.getFileName().toString();
                    String kid = name.substring(0, name.length() - PUBLIC_SUFFIX.length());
                    if (!KID.matcher(kid).matches()) {
                        throw new IllegalStateException("Invalid kid in file name: " + name);
                    }
                    publicKeys.put(kid, readPublic(file));
                }
            }
            RSAPrivateKey signingKey = readPrivate(dir.resolve(activeKid + PRIVATE_SUFFIX));

            JwtKeyRing ring = new JwtKeyRing(activeKid, signingKey, publicKeys);
            log.info("JWT keys loaded: active kid={}, verification kids={}", activeKid, publicKeys.keySet());
            return ring;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load JWT keys from " + dir.toAbsolutePath(), e);
        }
    }

    private static RSAPublicKey readPublic(Path file) throws IOException, GeneralSecurityException {
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decodePem(file)));
        requireStrength(key.getModulus().bitLength(), file);
        return key;
    }

    private static RSAPrivateKey readPrivate(Path file) throws IOException, GeneralSecurityException {
        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decodePem(file)));
        requireStrength(key.getModulus().bitLength(), file);
        return key;
    }

    private static void requireStrength(int bits, Path file) {
        if (bits < MIN_KEY_BITS) {
            throw new IllegalStateException("RSA key too short (" + bits + " bits): " + file.getFileName());
        }
    }

    private static byte[] decodePem(Path file) throws IOException {
        String pem = Files.readString(file);
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
