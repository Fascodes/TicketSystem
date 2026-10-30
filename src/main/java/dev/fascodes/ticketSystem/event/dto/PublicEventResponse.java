package dev.fascodes.ticketSystem.event.dto;

import java.time.Instant;
import java.util.UUID;

// Public catalog view - no owner or publication state
public record PublicEventResponse(
        UUID id,
        UUID venueId,
        String name,
        Instant startDate,
        Instant endDate,
        Instant ticketSaleDate) {}
