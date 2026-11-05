package dev.fascodes.ticketSystem.event.service;

import dev.fascodes.ticketSystem.common.SortPolicy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

final class EventSort {

    private static final SortPolicy POLICY = new SortPolicy(
            Set.of("name", "category", "startDate", "endDate", "ticketSaleDate"),
            Sort.by("startDate"));

    private EventSort() {}

    static Pageable sanitize(Pageable pageable) {
        return POLICY.apply(pageable);
    }
}
