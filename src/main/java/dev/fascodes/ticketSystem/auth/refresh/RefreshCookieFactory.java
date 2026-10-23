package dev.fascodes.ticketSystem.auth.refresh;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {

    private static final String NAME = "refresh_token";

    public ResponseCookie create(String rawToken, Duration ttl) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true)
                .secure(true)            // TODO: Create a dev profile, can turn this off for easier testing
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(ttl)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }
}
