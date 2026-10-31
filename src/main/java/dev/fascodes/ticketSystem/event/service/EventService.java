package dev.fascodes.ticketSystem.event.service;

import dev.fascodes.ticketSystem.common.PageResponse;
import dev.fascodes.ticketSystem.event.dto.EventFilter;
import dev.fascodes.ticketSystem.event.dto.EventRequest;
import dev.fascodes.ticketSystem.event.dto.EventResponse;
import dev.fascodes.ticketSystem.event.dto.PublicEventResponse;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.exception.EventNotFoundException;
import dev.fascodes.ticketSystem.event.mapper.EventMapper;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import dev.fascodes.ticketSystem.venue.exception.VenueNotFoundException;
import dev.fascodes.ticketSystem.venue.repository.VenueRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static dev.fascodes.ticketSystem.event.repository.EventSpecifications.*;

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

    // published=true is always enforced here - the public filter has no way to override it
    @Transactional(readOnly = true)
    public PageResponse<PublicEventResponse> searchPublished(EventFilter filter, Pageable pageable) {
        Specification<Event> spec = combine(isPublished(), filterSpec(filter));
        return PageResponse.from(eventRepository.findAll(spec, EventSort.sanitize(pageable))
                .map(eventMapper::toPublicResponse));
    }

    // Admin sees every event, anyone else only their own - owner id comes from the principal, never from the request
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchManaged(EventFilter filter, Boolean published, Pageable pageable,
                                                     Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        Specification<Event> spec = combine(
                admin ? null : ownedBy(user.id()),
                published(published),
                filterSpec(filter));
        return PageResponse.from(eventRepository.findAll(spec, EventSort.sanitize(pageable))
                .map(eventMapper::toResponse));
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

    private static Specification<Event> filterSpec(EventFilter filter) {
        return combine(
                nameContains(filter.name()),
                hasVenue(filter.venueId()),
                inCategories(filter.category()),
                startsFrom(filter.from()),
                startsTo(filter.to()));
    }

    // Empty filters come back as null - drop them before AND-ing the rest
    @SafeVarargs
    private static Specification<Event> combine(Specification<Event>... specs) {
        return Specification.allOf(Arrays.stream(specs).filter(Objects::nonNull).toList());
    }
}
