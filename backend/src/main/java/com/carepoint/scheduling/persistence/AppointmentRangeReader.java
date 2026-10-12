package com.carepoint.scheduling.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-side queries against appointments' tstzrange column — the GiST
 * overlap probe from docs/09 §2.3. Appointments gain a JPA entity with
 * the booking write path (next milestone); for the availability probe a
 * parameterized JDBC read is the honest minimum.
 */
@Component
public class AppointmentRangeReader {

    private final JdbcTemplate jdbc;

    public AppointmentRangeReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record BookedRange(Instant start, Instant end) {
    }

    /** BOOKED appointment ranges overlapping [from, to) for one doctor. */
    public List<BookedRange> bookedOverlapping(UUID doctorId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT lower(slot), upper(slot)
                FROM appointments
                WHERE doctor_id = ? AND status = 'BOOKED'
                  AND slot && tstzrange(?, ?, '[)')
                """,
                (rs, i) -> new BookedRange(
                        rs.getObject(1, OffsetDateTime.class).toInstant(),
                        rs.getObject(2, OffsetDateTime.class).toInstant()),
                doctorId, OffsetDateTime.ofInstant(from, java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, java.time.ZoneOffset.UTC));
    }
}
