package com.konkuk.memory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "questions", uniqueConstraints = @UniqueConstraint(name = "uk_questions_session_order", columnNames = {"training_session_id", "question_order"}))
public class Question extends BaseEntity {
    public enum Type { PHOTO_CLUE, EXPERIENCE, CONTEXT, INFORMATION_CHECK }
    public enum Format { MULTIPLE_CHOICE, FREE_TEXT }
    public enum Difficulty { EASY, MEDIUM, HARD }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_questions_session"))
    private TrainingSession trainingSession;
    @Column(nullable = false)
    private int questionOrder;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private Type type;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String evidenceSnapshot;
    @Column(nullable = false)
    private boolean verifiable;
    @Column(columnDefinition = "TEXT")
    private String referenceInformation;
    @Column(nullable = false, length = 100)
    private String generationVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Format format;
    @Column(columnDefinition = "TEXT")
    private String optionsJson;
    @Column(columnDefinition = "TEXT")
    private String answerKey;

    protected Question() {}

    public Question(TrainingSession session, int order, String content, Type type,
                    String evidenceSnapshot, boolean verifiable, String referenceInformation, String generationVersion) {
        this(session, order, content, type, evidenceSnapshot, verifiable, referenceInformation,
                generationVersion, Format.FREE_TEXT, null, null);
    }

    public Question(TrainingSession session, int order, String content, Type type,
                    String evidenceSnapshot, boolean verifiable, String referenceInformation,
                    String generationVersion, Format format, String optionsJson, String answerKey) {
        this.trainingSession = session;
        this.questionOrder = order;
        this.content = content;
        this.type = type;
        this.evidenceSnapshot = evidenceSnapshot;
        this.verifiable = verifiable;
        this.referenceInformation = referenceInformation;
        this.generationVersion = generationVersion;
        this.format = format;
        this.optionsJson = optionsJson;
        this.answerKey = answerKey;
    }

    public TrainingSession getTrainingSession() { return trainingSession; }
    public int getQuestionOrder() { return questionOrder; }
    public String getContent() { return content; }
    public Type getType() { return type; }
    public String getEvidenceSnapshot() { return evidenceSnapshot; }
    public boolean isVerifiable() { return verifiable; }
    public String getReferenceInformation() { return referenceInformation; }
    public String getGenerationVersion() { return generationVersion; }
    public Format getFormat() { return format; }
    public String getOptionsJson() { return optionsJson; }
    public String getAnswerKey() { return answerKey; }
}
