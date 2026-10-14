package com.carepoint.booking.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeskBookRequest(
        @NotNull UUID doctorId,
        @NotNull Instant start,
        @NotNull Instant end,
        @Size(max = 500) String reason,
        UUID patientId,
        @Valid InlinePatient patient) {

    /** Walk-in / phone booking without an account — creates a claimable guest. */
    public record InlinePatient(
            @NotNull @Email String email,
            @NotNull @Size(max = 200) String fullName,
            @Size(max = 40) String phone) {
    }
}
