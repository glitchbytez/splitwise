package com.glitchbytez.splitwise.common.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * The page envelope returned by every list endpoint.
 * A stable, explicit shape instead of serialising Spring Data's {@link Page} directly.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
