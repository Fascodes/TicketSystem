package dev.fascodes.ticketSystem.event.service;

import dev.fascodes.ticketSystem.common.InvalidSortException;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

// Sorting only by whitelisted columns - otherwise a client could order by owner.* or other
// internal paths (information leak through ordering, or a 500 on an unknown property)
final class EventSort {

    private static final Set<String> ALLOWED = Set.of("name", "category", "startDate", "endDate", "ticketSaleDate");
    private static final Sort DEFAULT = Sort.by("startDate");
    // Unique tie-breaker keeps page boundaries stable when sort values repeat
    private static final Sort TIE_BREAKER = Sort.by("id");

    private EventSort() {}

    static Pageable sanitize(Pageable pageable) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!ALLOWED.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), ALLOWED);
            }
        }
        Sort base = sort.isSorted() ? sort : DEFAULT;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), base.and(TIE_BREAKER));
    }
}
