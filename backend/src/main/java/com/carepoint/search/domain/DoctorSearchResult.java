package com.carepoint.search.domain;

import java.time.LocalDate;
import java.util.UUID;

public record DoctorSearchResult(
    UUID doctorId,
    UUID clinicId,
    String clinicName,
    String doctorName,
    String specialtyName,
    String bio,
    Integer consultMinutes,
    String location,
    Double rating,
    LocalDate nextAvailableSlot,
    String photoUrl
) {
}
