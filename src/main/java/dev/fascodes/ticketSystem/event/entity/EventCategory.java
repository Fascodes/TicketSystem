package dev.fascodes.ticketSystem.event.entity;

// Platform is music-only. Mapped to the PostgreSQL enum type event_category (see V3 migration)
public enum EventCategory {
    ROCK,
    POP,
    JAZZ,
    CLASSICAL,
    ELECTRONIC,
    HIP_HOP,
    METAL,
    OTHER
}
