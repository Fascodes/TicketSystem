package dev.fascodes.ticketSystem.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

// Used for both create (POST) and full update (PUT)
public record EventRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID venueId,
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        @NotNull Instant ticketSaleDate,
        boolean published) {}
