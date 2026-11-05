package dev.fascodes.ticketSystem.reservation.entity;

import dev.fascodes.ticketSystem.reservation.repository.ReservationRepository;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("reservationAccess")
public class ReservationAccessPolicy {

    private final ReservationRepository reservationRepository;

    public ReservationAccessPolicy(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public boolean isOwner(UUID reservationId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return reservationRepository.existsByIdAndUserId(reservationId, user.id());
    }
}
