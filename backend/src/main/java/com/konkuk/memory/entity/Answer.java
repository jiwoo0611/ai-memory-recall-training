package com.konkuk.memory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "answers", uniqueConstraints = @UniqueConstraint(name = "uk_answers_question", columnNames = "question_id"))
public class Answer extends BaseEntity {
    public enum Status { ANSWERED, CANNOT_RECALL, QUESTION_MISMATCH }
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, foreignKey = @ForeignKey(name = "fk_answers_question"))
    private Question question;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private Status status;
    @Column(columnDefinition = "TEXT")
    private String content;
    private Integer selectedOptionIndex;
    private Boolean correct;

    protected Answer() {}
    public Answer(Question question, Status status, String content) {
        this.question = question;
        this.status = status;
        this.content = content;
    }
    public Question getQuestion() { return question; }
    public Status getStatus() { return status; }
    public String getContent() { return content; }
    public Integer getSelectedOptionIndex() { return selectedOptionIndex; }
    public Boolean getCorrect() { return correct; }
    public static Answer quiz(Question question, int selectedIndex, String selectedOption, boolean correct) {
        Answer answer = new Answer(question, Status.ANSWERED, selectedOption);
        answer.selectedOptionIndex = selectedIndex;
        answer.correct = correct;
        return answer;
    }
}
