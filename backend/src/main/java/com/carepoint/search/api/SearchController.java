package com.carepoint.search.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.search.api.dto.SearchRequest;
import com.carepoint.search.application.SearchService;
import com.carepoint.search.domain.DoctorSearchResult;
import com.carepoint.search.domain.SearchResultPage;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/doctors")
    public ResponseEntity<SearchResultPage<DoctorSearchResult>> searchDoctors(
        @Valid SearchRequest request
    ) {
        return ResponseEntity.ok(searchService.searchDoctors(request));
    }

    @GetMapping("/doctors/{doctorId}")
    public ResponseEntity<DoctorSearchResult> getDoctor(
        @PathVariable java.util.UUID doctorId
    ) {
        return ResponseEntity.ok(searchService.findDoctorById(doctorId));
    }
}
