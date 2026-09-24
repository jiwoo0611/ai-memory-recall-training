package com.konkuk.memory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "results", uniqueConstraints = @UniqueConstraint(name = "uk_results_session", columnNames = "training_session_id"))
public class Result extends BaseEntity {
    public enum FeedbackStatus { PENDING, PROCESSING, COMPLETED, FAILED }
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_results_session"))
    private TrainingSession trainingSession;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private FeedbackStatus feedbackStatus = FeedbackStatus.PENDING;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(nullable = false)
    private int answeredCount;
    @Column(nullable = false)
    private int cannotRecallCount;
    @Column(nullable = false)
    private int mismatchCount;
    @Column(length = 100)
    private String generationVersion;
    @Version
    @Column(nullable = false)
    private long version;

    protected Result() {}
    public Result(TrainingSession session) { this.trainingSession = session; }
    public TrainingSession getTrainingSession() { return trainingSession; }
    public FeedbackStatus getFeedbackStatus() { return feedbackStatus; }
    public String getSummary() { return summary; }
    public int getAnsweredCount() { return answeredCount; }
    public int getCannotRecallCount() { return cannotRecallCount; }
    public int getMismatchCount() { return mismatchCount; }
    public String getGenerationVersion() { return generationVersion; }
    public long getVersion() { return version; }
}
