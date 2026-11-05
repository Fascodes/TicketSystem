package dev.fascodes.ticketSystem.reservation.mapper;

import dev.fascodes.ticketSystem.reservation.dto.ReservationResponse;
import dev.fascodes.ticketSystem.reservation.entity.Reservation;

import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getPaidAt(),
                reservation.getCancelledAt());
    }
}
