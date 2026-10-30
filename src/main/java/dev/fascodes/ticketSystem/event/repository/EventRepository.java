package dev.fascodes.ticketSystem.event.repository;

import dev.fascodes.ticketSystem.event.entity.Event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    boolean existsByIdAndOwnerId(UUID eventId, UUID id);

    boolean existsByIdAndPublishedTrue(UUID eventId);

    Optional<Event> findByIdAndPublishedTrue(UUID id);

    @Query("""
    select count(e) > 0 from Event e
    where e.id = :id and (e.published = true or e.owner.id = :userId)
    """)
    boolean canView(@Param("id") UUID id, @Param("userId") UUID userId);
}
