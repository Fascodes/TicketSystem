package dev.fascodes.ticketSystem.common;

import java.util.Collection;
import java.util.TreeSet;

public class InvalidSortException extends RuntimeException {
    public InvalidSortException(String property, Collection<String> allowed) {
        super("Cannot sort by '" + property + "', allowed: " + String.join(", ", new TreeSet<>(allowed)));
    }
}
