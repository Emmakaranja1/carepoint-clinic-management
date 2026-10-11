package com.carepoint.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the database is authoritative against double-booking: N concurrent
 * inserts for the same exclusive slot must yield exactly one success; every
 * other attempt must fail with the exclusion constraint (SQLState 23P01),
 * which the service layer will map to 409 SLOT_TAKEN.
 */
@Testcontainers
@SpringBootTest
class BookingConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentInsertsForSameSlot_exactlyOneSucceeds() throws Exception {
        SchemaFixture f = SchemaFixture.create(jdbc);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        int attempts = 8;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(insertBooking(f, start, end, ready, fire)));
            }
            // all threads staged, release them simultaneously
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<Boolean> r : results) {
                if (r.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                } else {
                    rejected++;
                }
            }

            assertThat(succeeded).as("winning inserts").isEqualTo(1);
            assertThat(rejected).as("constraint violations").isEqualTo(attempts - 1);

            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM appointments WHERE doctor_id = ? AND status = 'BOOKED'",
                    Integer.class, f.doctorId);
            assertThat(rows).isEqualTo(1);
        } finally {
            fire.countDown();
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> insertBooking(
            SchemaFixture f, Instant start, Instant end, CountDownLatch ready, CountDownLatch fire) {
        return () -> {
            ready.countDown();
            fire.await();
            try {
                jdbc.update(
                        "INSERT INTO appointments "
                                + "(id, clinic_id, doctor_id, patient_id, slot, created_by) "
                                + "VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)",
                        UUID.randomUUID(), f.clinicId, f.doctorId, f.patientId,
                        Timestamp.from(start), Timestamp.from(end), f.staffUserId);
                return true;
            } catch (DataIntegrityViolationException e) {
                // the exclusion constraint name must be the cause — not some
                // unrelated integrity failure
                assertThat(rootMessage(e)).contains("no_overlapping_bookings");
                return false;
            }
        };
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
