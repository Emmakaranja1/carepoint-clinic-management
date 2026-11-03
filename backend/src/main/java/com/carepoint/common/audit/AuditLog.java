package com.carepoint.common.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Append-only writer for {@code audit_events} (docs/06 §8). Joins the
 * caller's transaction so an audited change and its event commit together.
 * Metadata carries identifiers and enum values only — never tokens,
 * passwords or free text.
 */
@Component
public class AuditLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(UUID clinicId, UUID actorId, String action, UUID targetId,
                       Map<String, ?> metadata) {
        jdbc.update("""
                INSERT INTO audit_events (clinic_id, actor_id, action, target_id, metadata)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, clinicId, actorId, action, targetId, toJson(metadata));
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
