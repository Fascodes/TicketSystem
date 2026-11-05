package dev.fascodes.ticketSystem.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

// Sorting only by whitelisted columns - otherwise a client could order by owner.* or other
// internal paths (information leak through ordering, or a 500 on an unknown property)
public final class SortPolicy {

    // Unique tie-breaker keeps page boundaries stable when sort values repeat
    private static final Sort TIE_BREAKER = Sort.by("id");

    private final Set<String> allowed;
    private final Sort defaultSort;

    public SortPolicy(Set<String> allowed, Sort defaultSort) {
        this.allowed = Set.copyOf(allowed);
        this.defaultSort = defaultSort;
    }

    public Pageable apply(Pageable pageable) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), allowed);
            }
        }
        Sort base = sort.isSorted() ? sort : defaultSort;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), base.and(TIE_BREAKER));
    }
}
