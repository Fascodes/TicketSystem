package dev.fascodes.ticketSystem.reservation.entity;

// PENDING -> PAID | FAILED | CANCELLED. Only PAID takes seats from the event pool.
// Mapped to the PostgreSQL enum type reservation_status (see V4 migration)
public enum ReservationStatus {
    PENDING,
    PAID,
    // Payment went through but the pool ran out first - needs a refund (TODO, out of MVP)
    FAILED,
    CANCELLED
}
