package com.konkuk.memory.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Transactional outbox: rollback keeps the image; committed deletions retry after restart. */
@Service
@EnableScheduling
public class StorageDeletionQueue {
    private final JdbcTemplate jdbc;
    private final FileStorageService storage;
    public StorageDeletionQueue(JdbcTemplate jdbc, FileStorageService storage) { this.jdbc = jdbc; this.storage = storage; }
    public void enqueue(String key) {
        jdbc.update("INSERT INTO storage_deletions(storage_key, created_at) VALUES (?, CURRENT_TIMESTAMP)", key);
    }
    @Scheduled(fixedDelayString = "${app.storage.cleanup-delay:10000}")
    public void clean() {
        for (String key : jdbc.queryForList("SELECT storage_key FROM storage_deletions ORDER BY created_at LIMIT 100", String.class)) {
            try {
                storage.delete(key);
                jdbc.update("DELETE FROM storage_deletions WHERE storage_key=?", key);
            } catch (RuntimeException ignored) { /* Durable row remains for the next attempt. */ }
        }
    }
}
