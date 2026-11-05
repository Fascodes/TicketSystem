package dev.fascodes.ticketSystem.reservation.dto;

import dev.fascodes.ticketSystem.reservation.entity.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        int quantity,
        ReservationStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant cancelledAt) {}
