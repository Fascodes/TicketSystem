package dev.fascodes.ticketSystem.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {}
