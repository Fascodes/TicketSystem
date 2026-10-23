package dev.fascodes.ticketSystem.auth.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("Email already taken");
    }
}
