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
    private Integer totalCount;
    private Integer correctCount;
    private Integer incorrectCount;
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
    public Integer getTotalCount() { return totalCount; }
    public Integer getCorrectCount() { return correctCount; }
    public Integer getIncorrectCount() { return incorrectCount; }
    public void recordQuizScore(int total, int correct) {
        if (total < 1 || correct < 0 || correct > total) throw new IllegalArgumentException("Invalid quiz score");
        this.totalCount = total;
        this.answeredCount = total;
        this.correctCount = correct;
        this.incorrectCount = total - correct;
    }
    public void startFeedback() { this.feedbackStatus = FeedbackStatus.PROCESSING; }
    public void completeQuizFeedback(String summary) {
        this.summary = summary;
        this.feedbackStatus = FeedbackStatus.COMPLETED;
        this.generationVersion = "quiz-rules-v1";
    }
    public void setCounts(int answered, int cannotRecall, int mismatch) {
        this.answeredCount = answered; this.cannotRecallCount = cannotRecall; this.mismatchCount = mismatch;
    }
    public void completeFeedback(String summary) {
        this.summary = summary; this.feedbackStatus = FeedbackStatus.COMPLETED;
        this.generationVersion = "extractive-recall-v1";
    }
    public void failFeedback() { this.feedbackStatus = FeedbackStatus.FAILED; }
}
