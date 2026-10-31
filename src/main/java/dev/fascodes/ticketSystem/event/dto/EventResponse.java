package dev.fascodes.ticketSystem.event.dto;

import dev.fascodes.ticketSystem.event.entity.EventCategory;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID ownerId,
        UUID venueId,
        String name,
        EventCategory category,
        Instant startDate,
        Instant endDate,
        Instant ticketSaleDate,
        boolean published) {}
