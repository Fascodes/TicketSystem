package dev.fascodes.ticketSystem.event.service;

import dev.fascodes.ticketSystem.event.dto.EventRequest;
import dev.fascodes.ticketSystem.event.dto.EventResponse;
import dev.fascodes.ticketSystem.event.dto.PublicEventResponse;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.exception.EventNotFoundException;
import dev.fascodes.ticketSystem.event.mapper.EventMapper;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import dev.fascodes.ticketSystem.venue.exception.VenueNotFoundException;
import dev.fascodes.ticketSystem.venue.repository.VenueRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final EventMapper eventMapper;

    public EventService(EventRepository eventRepository, VenueRepository venueRepository, UserRepository userRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.userRepository = userRepository;
        this.eventMapper = eventMapper;
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public EventResponse create(EventRequest req, UUID ownerId) {
        Event event = new Event();
        event.setOwner(userRepository.getReferenceById(ownerId));
        event.setVenue(findVenue(req.venueId()));
        eventMapper.apply(req, event);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    @PreAuthorize("@eventAccess.isOwner(#id, authentication) or hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EventResponse get(UUID id) {
        return eventMapper.toResponse(findEvent(id));
    }

    // Unpublished and missing events are indistinguishable (404) - existence is not leaked
    @Transactional(readOnly = true)
    public PublicEventResponse getPublished(UUID id) {
        return eventRepository.findByIdAndPublishedTrue(id)
                .map(eventMapper::toPublicResponse)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    @PreAuthorize("@eventAccess.isOwner(#id, authentication) or hasRole('ADMIN')")
    @Transactional
    public EventResponse update(UUID id, EventRequest req) {
        Event event = findEvent(id);
        if (!event.getVenue().getId().equals(req.venueId())) {
            event.setVenue(findVenue(req.venueId()));
        }
        eventMapper.apply(req, event);
        return eventMapper.toResponse(event);
    }

    @PreAuthorize("@eventAccess.isOwner(#id, authentication) or hasRole('ADMIN')")
    @Transactional
    public void delete(UUID id) {
        eventRepository.delete(findEvent(id));
    }

    private Event findEvent(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    private Venue findVenue(UUID id) {
        return venueRepository.findById(id).orElseThrow(() -> new VenueNotFoundException(id));
    }
}
