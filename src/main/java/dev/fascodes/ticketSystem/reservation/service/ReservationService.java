package dev.fascodes.ticketSystem.reservation.service;

import dev.fascodes.ticketSystem.common.PageResponse;
import dev.fascodes.ticketSystem.common.SortPolicy;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.exception.EventNotFoundException;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.reservation.dto.ReservationRequest;
import dev.fascodes.ticketSystem.reservation.dto.ReservationResponse;
import dev.fascodes.ticketSystem.reservation.entity.Reservation;
import dev.fascodes.ticketSystem.reservation.entity.ReservationStatus;
import dev.fascodes.ticketSystem.reservation.exception.ReservationConflictException;
import dev.fascodes.ticketSystem.reservation.exception.ReservationNotFoundException;
import dev.fascodes.ticketSystem.reservation.mapper.ReservationMapper;
import dev.fascodes.ticketSystem.reservation.repository.ReservationRepository;
import dev.fascodes.ticketSystem.user.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// Seat pool rules:
// - PENDING does not hold seats, so nobody can block the pool without paying
// - seats are taken only on payment, under SELECT ... FOR UPDATE on the event
// - every method touching status or pool locks the EVENT FIRST, then reads the reservation -
//   one lock order for all paths means no deadlocks
@Service
public class ReservationService {

    private static final SortPolicy SORT = new SortPolicy(
            Set.of("createdAt", "status", "quantity"),
            Sort.by(Sort.Direction.DESC, "createdAt"));

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository, EventRepository eventRepository,
                              UserRepository userRepository, ReservationMapper reservationMapper, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.reservationMapper = reservationMapper;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ReservationResponse create(ReservationRequest req, UUID userId) {
        // Unpublished events behave like missing ones - same as the public API
        Event event = eventRepository.findByIdAndPublishedTrue(req.eventId())
                .orElseThrow(() -> new EventNotFoundException(req.eventId()));
        Instant now = clock.instant();
        requireSaleOpen(event, now);
        // Soft check without a lock: rejects sold-out events early, the pool itself is untouched.
        // The authoritative check happens in pay() under the lock.
        if (event.getAvailableSeats() < req.quantity()) {
            throw new ReservationConflictException("Not enough seats available");
        }

        Reservation reservation = new Reservation();
        reservation.setEvent(event);
        reservation.setUser(userRepository.getReferenceById(userId));
        reservation.setQuantity(req.quantity());
        reservation.setCreatedAt(now);
        try {
            // Flush now so a uq_reservations_open violation surfaces here, not at commit
            return reservationMapper.toResponse(reservationRepository.saveAndFlush(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new ReservationConflictException("An unpaid reservation for this event already exists");
        }
    }

    @PreAuthorize("@reservationAccess.isOwner(#id, authentication) or hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return reservationMapper.toResponse(findReservation(id));
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findMine(UUID userId, ReservationStatus status, Pageable pageable) {
        Pageable sorted = SORT.apply(pageable);
        Page<Reservation> page = status == null
                ? reservationRepository.findByUserId(userId, sorted)
                : reservationRepository.findByUserIdAndStatus(userId, status, sorted);
        return PageResponse.from(page.map(reservationMapper::toResponse));
    }

    // MVP stand-in for a payment provider confirmation (TODO: replace with a verified webhook).
    // Idempotent: confirming an already PAID reservation again changes nothing.
    @PreAuthorize("@reservationAccess.isOwner(#id, authentication)")
    @Transactional
    public ReservationResponse pay(UUID id) {
        Event event = lockEventOf(id);
        Reservation reservation = findReservation(id);

        switch (reservation.getStatus()) {
            case PAID -> { return reservationMapper.toResponse(reservation); }
            case FAILED, CANCELLED -> throw new ReservationConflictException(
                    "Reservation is " + reservation.getStatus() + " and cannot be paid");
            case PENDING -> { }
        }

        Instant now = clock.instant();
        // FAILED is a business outcome, not an exception - throwing would roll the status back
        if (!now.isBefore(event.getTicketSaleDate()) || event.getAvailableSeats() < reservation.getQuantity()) {
            reservation.setStatus(ReservationStatus.FAILED);
            // TODO: refund - the payment was taken but no seats are left
        } else {
            event.setAvailableSeats(event.getAvailableSeats() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.PAID);
            reservation.setPaidAt(now);
        }
        return reservationMapper.toResponse(reservation);
    }

    // Only PENDING can be cancelled - it holds no seats, so the pool stays as is.
    // The event is still locked so a concurrent pay() cannot flip the same reservation to PAID.
    // TODO: cancelling PAID (seats back to the pool + refund) - admin flow, out of MVP
    @PreAuthorize("@reservationAccess.isOwner(#id, authentication) or hasRole('ADMIN')")
    @Transactional
    public ReservationResponse cancel(UUID id) {
        lockEventOf(id);
        Reservation reservation = findReservation(id);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ReservationConflictException(
                    "Reservation is " + reservation.getStatus() + " and cannot be cancelled");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(clock.instant());
        return reservationMapper.toResponse(reservation);
    }

    private Event lockEventOf(UUID reservationId) {
        UUID eventId = reservationRepository.findEventIdById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private Reservation findReservation(UUID id) {
        return reservationRepository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private static void requireSaleOpen(Event event, Instant now) {
        if (now.isBefore(event.getTicketSaleDate())) {
            throw new ReservationConflictException("Ticket sale has not started yet");
        }
        if (!now.isBefore(event.getStartDate())) {
            throw new ReservationConflictException("Event has already started");
        }
    }
}
