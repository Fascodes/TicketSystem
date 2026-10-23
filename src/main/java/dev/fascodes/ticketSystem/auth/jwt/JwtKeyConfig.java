package dev.fascodes.ticketSystem.auth.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey(JwtProperties props) {
        try {
            byte[] der = decodePem(props.privateKey());
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot load JWT private key", e);
        }
    }

    @Bean
    public RSAPublicKey jwtPublicKey(JwtProperties props) {
        try {
            byte[] der = decodePem(props.publicKey());
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot load JWT public key", e);
        }
    }

    private static byte[] decodePem(Resource resource) throws IOException {
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}