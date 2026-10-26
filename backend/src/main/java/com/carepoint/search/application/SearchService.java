package com.carepoint.search.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.catalog.domain.DoctorProfile;
import com.carepoint.search.api.dto.SearchRequest;
import com.carepoint.search.domain.DoctorSearchResult;
import com.carepoint.search.domain.SearchResultPage;
import com.carepoint.search.persistence.SearchRepository;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public SearchResultPage<DoctorSearchResult> searchDoctors(SearchRequest request) {
        var offset = request.page() * request.size();

        var results = searchRepository.searchDoctors(
            request.query(),
            request.specialtyId(),
            request.location(),
            request.size(),
            offset
        );

        var total = searchRepository.countDoctors(
            request.query(),
            request.specialtyId(),
            request.location()
        );

        return SearchResultPage.of(results, total, request.page(), request.size());
    }

    public DoctorSearchResult findDoctorById(UUID doctorId) {
        return searchRepository.findDoctorById(doctorId)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
    }
}
