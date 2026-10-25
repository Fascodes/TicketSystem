package dev.fascodes.ticketSystem.event.entity;

import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("eventAccess")
public class EventAccessPolicy {

    private final EventRepository eventRepository;

    public EventAccessPolicy(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public boolean isOwner(UUID eventId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return eventRepository.existsByIdAndOwnerId(eventId, user.id());
    }

    public boolean isPublic(UUID eventId){
        return eventRepository.existsByIdAndIsPublic(eventId);
    }
}
