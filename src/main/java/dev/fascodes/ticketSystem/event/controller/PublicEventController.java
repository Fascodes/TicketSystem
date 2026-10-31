package dev.fascodes.ticketSystem.event.controller;

import dev.fascodes.ticketSystem.common.PageResponse;
import dev.fascodes.ticketSystem.event.dto.EventFilter;
import dev.fascodes.ticketSystem.event.dto.PublicEventResponse;
import dev.fascodes.ticketSystem.event.service.EventService;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final EventService eventService;

    public PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    // e.g. ?name=jazz&category=JAZZ&category=ROCK&from=...&to=...&page=0&size=20&sort=startDate,asc
    @GetMapping
    public PageResponse<PublicEventResponse> search(@Valid EventFilter filter, Pageable pageable) {
        return eventService.searchPublished(filter, pageable);
    }

    @GetMapping("/{id}")
    public PublicEventResponse get(@PathVariable UUID id) {
        return eventService.getPublished(id);
    }
}
