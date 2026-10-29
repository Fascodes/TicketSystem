package dev.fascodes.ticketSystem.auth.jwt;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;

public record JwtKeyRing(String activeKid, RSAPrivateKey signingKey, Map<String, RSAPublicKey> publicKeys) {

    public JwtKeyRing {
        publicKeys = Map.copyOf(publicKeys);
        RSAPublicKey activePublic = publicKeys.get(activeKid);
        if (activePublic == null) {
            throw new IllegalStateException("No public key for active kid '" + activeKid + "'");
        }
        if (!activePublic.getModulus().equals(signingKey.getModulus())) {
            throw new IllegalStateException("Private and public key for kid '" + activeKid + "' do not match");
        }
    }

    public Optional<RSAPublicKey> publicKey(String kid) {
        return Optional.ofNullable(kid).map(publicKeys::get);
    }
}
