package dev.fascodes.ticketSystem.event.dto;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID ownerId,
        UUID venueId,
        String name,
        Instant startDate,
        Instant endDate,
        Instant ticketSaleDate,
        boolean published) {}
