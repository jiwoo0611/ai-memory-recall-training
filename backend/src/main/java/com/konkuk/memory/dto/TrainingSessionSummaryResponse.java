package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.entity.TrainingSession;
import java.time.Instant;

public record TrainingSessionSummaryResponse(Long id, PhotoInfo photo, TrainingSession.Status status,
                                             Instant startedAt, Instant completedAt) {
    public record PhotoInfo(Long id, String originalFilename) {
        public static PhotoInfo from(Photo photo) { return new PhotoInfo(photo.getId(), photo.getOriginalFilename()); }
    }

    public static TrainingSessionSummaryResponse from(TrainingSession session) {
        return new TrainingSessionSummaryResponse(session.getId(), PhotoInfo.from(session.getPhoto()),
                session.getStatus(), session.getStartedAt(), session.getCompletedAt());
    }
}
