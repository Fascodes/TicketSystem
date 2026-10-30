package dev.fascodes.ticketSystem.event.controller;

import dev.fascodes.ticketSystem.event.dto.PublicEventResponse;
import dev.fascodes.ticketSystem.event.service.EventService;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final EventService eventService;

    public PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/{id}")
    public PublicEventResponse get(@PathVariable UUID id) {
        return eventService.getPublished(id);
    }
}
