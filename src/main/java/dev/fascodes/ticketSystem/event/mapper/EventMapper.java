package dev.fascodes.ticketSystem.event.mapper;

import dev.fascodes.ticketSystem.event.dto.EventRequest;
import dev.fascodes.ticketSystem.event.dto.EventResponse;
import dev.fascodes.ticketSystem.event.dto.PublicEventResponse;
import dev.fascodes.ticketSystem.event.entity.Event;

import org.springframework.stereotype.Component;

// Relations (owner, venue) are resolved in the service - mapper only copies plain fields
@Component
public class EventMapper {

    public EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getOwner().getId(),
                event.getVenue().getId(),
                event.getName(),
                event.getCategory(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTicketSaleDate(),
                event.isPublished());
    }

    public PublicEventResponse toPublicResponse(Event event) {
        return new PublicEventResponse(
                event.getId(),
                event.getVenue().getId(),
                event.getName(),
                event.getCategory(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTicketSaleDate());
    }

    public void apply(EventRequest req, Event event) {
        event.setName(req.name().trim());
        event.setCategory(req.category());
        event.setStartDate(req.startDate());
        event.setEndDate(req.endDate());
        event.setTicketSaleDate(req.ticketSaleDate());
        event.setPublished(req.published());
    }
}
