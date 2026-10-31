package dev.fascodes.ticketSystem.event.dto;

import dev.fascodes.ticketSystem.event.entity.EventCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// Bound from query params, every field optional. Deliberately has no published/owner fields -
// those are access constraints set by the service, never by the client.
public record EventFilter(
        @Size(max = 100) String name,
        UUID venueId,
        Set<EventCategory> category,
        Instant from,
        Instant to) {

    @AssertTrue(message = "'from' must not be after 'to'")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }
}
