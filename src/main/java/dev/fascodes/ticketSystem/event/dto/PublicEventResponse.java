package dev.fascodes.ticketSystem.event.dto;

import dev.fascodes.ticketSystem.event.entity.EventCategory;

import java.time.Instant;
import java.util.UUID;

// Public catalog view - no owner or publication state
public record PublicEventResponse(
        UUID id,
        UUID venueId,
        String name,
        EventCategory category,
        Instant startDate,
        Instant endDate,
        Instant ticketSaleDate) {}
