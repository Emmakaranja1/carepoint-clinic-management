package com.carepoint.search.domain;

import java.util.List;

public record SearchResultPage<T>(
    List<T> results,
    long total,
    int page,
    int size,
    int totalPages
) {
    public SearchResultPage {
        totalPages = (int) Math.ceil((double) total / size);
    }

    public static <T> SearchResultPage<T> of(List<T> results, long total, int page, int size) {
        return new SearchResultPage<>(results, total, page, size, 0);
    }
}
