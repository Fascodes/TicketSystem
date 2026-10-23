package dev.fascodes.ticketSystem.auth.user;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("Email already taken");
    }
}
