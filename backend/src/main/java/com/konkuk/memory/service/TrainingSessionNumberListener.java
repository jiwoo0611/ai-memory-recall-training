package com.konkuk.memory.service;

import com.konkuk.memory.entity.TrainingSession;
import jakarta.persistence.PrePersist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Assign once in the same transaction as the session insert, across every creation path. */
@Component
public class TrainingSessionNumberListener {
    private final JdbcTemplate db;
    public TrainingSessionNumberListener(JdbcTemplate db) { this.db=db; }

    @PrePersist
    public void assignNumber(TrainingSession session) {
        if (session.getAlbumSequence()!=null) return;
        long albumId=session.getPhoto().getAlbum().getId();
        // The update locks this album's counter until commit, serializing concurrent inserts.
        if (db.update("UPDATE albums SET next_training_number=next_training_number+1 WHERE id=?",albumId)!=1)
            throw new IllegalStateException("Training album does not exist");
        Long number=db.queryForObject("SELECT next_training_number FROM albums WHERE id=?",Long.class,albumId);
        if (number==null) throw new IllegalStateException("Training number allocation failed");
        session.assignAlbumSequence(number);
    }
}
