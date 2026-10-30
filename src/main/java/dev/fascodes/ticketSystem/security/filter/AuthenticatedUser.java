package dev.fascodes.ticketSystem.security.filter;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email) { }
