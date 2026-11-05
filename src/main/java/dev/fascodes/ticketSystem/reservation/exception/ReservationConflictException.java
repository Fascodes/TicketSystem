package dev.fascodes.ticketSystem.reservation.exception;

// Business rule violation that the client can understand and act on - mapped to 409 with this message
public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String message) {
        super(message);
    }
}
