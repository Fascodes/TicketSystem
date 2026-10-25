package dev.fascodes.ticketSystem.event.repository;

import dev.fascodes.ticketSystem.event.entity.Event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    boolean existsByIdAndOwnerId(UUID eventId, UUID id);

    boolean existsByIdAndIsPublic(UUID eventId);
}
