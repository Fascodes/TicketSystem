package dev.fascodes.ticketSystem.auth.controller;

import dev.fascodes.ticketSystem.auth.dto.AuthResponse;
import dev.fascodes.ticketSystem.auth.dto.LoginRequest;
import dev.fascodes.ticketSystem.auth.dto.RegisterRequest;
import dev.fascodes.ticketSystem.auth.exception.InvalidRefreshTokenException;
import dev.fascodes.ticketSystem.auth.jwt.JwtProperties;
import dev.fascodes.ticketSystem.auth.jwt.JwtService;
import dev.fascodes.ticketSystem.auth.refresh.RefreshCookieFactory;
import dev.fascodes.ticketSystem.auth.service.AuthService;
import dev.fascodes.ticketSystem.user.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshCookieFactory cookieFactory;
    private final JwtProperties props;

    public AuthController(AuthService authService, JwtService jwtService, RefreshCookieFactory cookieFactory, JwtProperties props) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest http) {
        var tokens = authService.login(req, http.getHeader("User-Agent"), http.getRemoteAddr());
        return respond(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        var tokens = authService.refresh(refreshToken, http.getHeader("User-Agent"), http.getRemoteAddr());
        return respond(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> respond(AuthService.Tokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.create(tokens.refreshToken(), props.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthResponse(tokens.accessToken(), "Bearer", jwtService.accessTtlSeconds()));
    }
}