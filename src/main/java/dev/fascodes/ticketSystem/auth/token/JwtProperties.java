package dev.fascodes.ticketSystem.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        Resource privateKey,
        Resource publicKey,
        String keyId,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl) {}
