package dev.fascodes.ticketSystem.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        Path keysDir,
        String activeKeyId,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl) {}
