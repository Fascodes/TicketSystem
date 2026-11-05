package dev.fascodes.ticketSystem.reservation.controller;

import dev.fascodes.ticketSystem.common.PageResponse;
import dev.fascodes.ticketSystem.reservation.dto.ReservationRequest;
import dev.fascodes.ticketSystem.reservation.dto.ReservationResponse;
import dev.fascodes.ticketSystem.reservation.entity.ReservationStatus;
import dev.fascodes.ticketSystem.reservation.service.ReservationService;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest req,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.create(req, user.id());
    }

    // Always the caller's own reservations - user id comes from the principal
    @GetMapping
    public PageResponse<ReservationResponse> findMine(@RequestParam(required = false) ReservationStatus status,
                                                      Pageable pageable,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.findMine(user.id(), status, pageable);
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return reservationService.get(id);
    }

    // TODO: MVP payment stub - replace with the payment provider's webhook
    @PostMapping("/{id}/pay")
    public ReservationResponse pay(@PathVariable UUID id) {
        return reservationService.pay(id);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return reservationService.cancel(id);
    }
}
