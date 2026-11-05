package dev.fascodes.ticketSystem.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID eventId,
        @Min(1) @Max(6) int quantity) {}
