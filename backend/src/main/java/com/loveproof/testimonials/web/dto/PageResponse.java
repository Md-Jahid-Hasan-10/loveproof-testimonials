package com.loveproof.testimonials.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A page of results.
 *
 * <p>The pagination contract is {@code page} (0-based) and {@code size}, with {@code total},
 * {@code totalPages} and {@code page} echoed in the response.
 *
 * @param content   the items on this page
 * @param page      the 0-based page number
 * @param size      the page size
 * @param total     the total number of matching items
 * @param totalPages the total number of pages
 * @param <T>       the item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages) {

    /**
     * Maps a Spring Data page to its response representation.
     *
     * @param page    the page
     * @param mapper  the item mapper
     * @param <S>     the entity type
     * @param <T>     the response type
     * @return the response
     */
    public static <S, T> PageResponse<T> from(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
