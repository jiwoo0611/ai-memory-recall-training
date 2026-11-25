package com.konkuk.memory.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@EntityListeners(com.konkuk.memory.service.TrainingSessionNumberListener.class)
@Table(name = "training_sessions", indexes = @Index(name = "idx_sessions_photo_created", columnList = "photo_id,created_at"))
public class TrainingSession extends BaseEntity {
    public enum Status { CREATED, GENERATING_QUESTIONS, IN_PROGRESS, COMPLETED, QUESTION_GENERATION_FAILED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sessions_photo"))
    private Photo photo;
    @Column(nullable = false, updatable = false)
    private Long albumSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private Status status = Status.CREATED;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant completedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected TrainingSession() {}
    public TrainingSession(Photo photo) {
        this.photo = photo;
        this.startedAt = Instant.now();
    }
    public Long getAlbumSequence() { return albumSequence; }
    public void assignAlbumSequence(long number) {
        if (albumSequence != null || number < 1) throw new IllegalStateException("Training number cannot be reassigned");
        albumSequence = number;
    }
    public String getDisplayName() {
        if (photo == null || photo.getAlbum() == null) return "훈련";
        return photo.getAlbum().getTitle() + (albumSequence == null ? "" : " " + albumSequence);
    }
    public Photo getPhoto() { return photo; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
    public void markCompleted() {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException("Only an in-progress session can complete");
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }
    public void markGeneratingQuestions() { this.status = Status.GENERATING_QUESTIONS; }
    public void markInProgress() { this.status = Status.IN_PROGRESS; }
    public void markQuestionGenerationFailed() { this.status = Status.QUESTION_GENERATION_FAILED; }
}
