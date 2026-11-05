package dev.fascodes.ticketSystem.reservation.repository;

import dev.fascodes.ticketSystem.reservation.entity.Reservation;
import dev.fascodes.ticketSystem.reservation.entity.ReservationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    boolean existsByIdAndUserId(UUID reservationId, UUID userId);

    // Scalar read - the reservation entity must not enter the persistence context before the event lock is held
    @Query("select r.event.id from Reservation r where r.id = :id")
    Optional<UUID> findEventIdById(@Param("id") UUID id);

    Page<Reservation> findByUserId(UUID userId, Pageable pageable);

    Page<Reservation> findByUserIdAndStatus(UUID userId, ReservationStatus status, Pageable pageable);
}
