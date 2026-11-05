package dev.fascodes.ticketSystem.event.exception;

public class CapacityBelowSoldException extends RuntimeException {
    public CapacityBelowSoldException(int requested, int sold) {
        super("Capacity " + requested + " is below the " + sold + " seats already sold");
    }
}
