package dev.fascodes.ticketSystem.event.repository;

import dev.fascodes.ticketSystem.event.entity.Event;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
    boolean existsByIdAndOwnerId(UUID eventId, UUID id);

    boolean existsByIdAndPublishedTrue(UUID eventId);

    Optional<Event> findByIdAndPublishedTrue(UUID id);

    @Query("""
    select count(e) > 0 from Event e
    where e.id = :id and (e.published = true or e.owner.id = :userId)
    """)
    boolean canView(@Param("id") UUID id, @Param("userId") UUID userId);

    // SELECT ... FOR UPDATE - serializes every change of the seat pool for one event.
    // Lock timeout keeps callers from hanging; a timeout surfaces as PessimisticLockingFailureException (409)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    // Venue fetched up front for upcoming venue/location data in listings; count query stays join-free
    @Override
    @EntityGraph(attributePaths = "venue")
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);
}
