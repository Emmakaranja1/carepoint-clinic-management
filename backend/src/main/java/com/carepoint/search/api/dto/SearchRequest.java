package com.carepoint.search.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
    @NotBlank(message = "Query is required")
    @Size(max = 200, message = "Query must not exceed 200 characters")
    String query,

    UUID specialtyId,

    String location,

    @Min(value = 0, message = "Page must be at least 0")
    int page,

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    int size
) {
    public SearchRequest {
        if (page < 0) page = 0;
        if (size < 1) size = 10;
        if (size > 100) size = 100;
    }

    public static SearchRequest of(String query) {
        return new SearchRequest(query, null, null, 0, 10);
    }
}
