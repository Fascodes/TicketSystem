package dev.fascodes.ticketSystem.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Stable JSON contract for paged results instead of serializing PageImpl directly
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
