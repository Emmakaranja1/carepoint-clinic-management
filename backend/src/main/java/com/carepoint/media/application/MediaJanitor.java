package com.carepoint.media.application;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.common.storage.StorageService;
import com.carepoint.media.domain.MediaObject;
import com.carepoint.media.persistence.MediaObjectRepository;

/**
 * Reaps PENDING uploads older than 24h — minted URLs that were never used,
 * or objects whose confirm failed (docs/06 §7b failure cleanup).
 */
@Component
public class MediaJanitor {

    private static final Logger log = LoggerFactory.getLogger(MediaJanitor.class);
    private static final Duration PENDING_TTL = Duration.ofHours(24);

    private final MediaObjectRepository media;
    private final StorageService storage;

    public MediaJanitor(MediaObjectRepository media, StorageService storage) {
        this.media = media;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void reapStaleUploads() {
        var stale = media.findByStatusAndCreatedAtBefore(
                "PENDING", Instant.now().minus(PENDING_TTL));
        for (MediaObject m : stale) {
            storage.delete(m.getObjectKey());
            m.markOrphaned();
        }
        if (!stale.isEmpty()) {
            log.info("Reaped {} stale media uploads", stale.size());
        }
    }
}
