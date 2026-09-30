package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Question;

import java.util.List;

public record QuizSubmissionResponse(
        Long sessionId,
        int totalCount,
        int correctCount,
        int incorrectCount,
        List<QuestionResult> results,
        TrainingSessionDetailResponse session
) {
    public QuizSubmissionResponse(Long sessionId, int totalCount, int correctCount, int incorrectCount, List<QuestionResult> results) {
        this(sessionId, totalCount, correctCount, incorrectCount, results, null);
    }
    public QuizSubmissionResponse withSession(TrainingSessionDetailResponse session) {
        return new QuizSubmissionResponse(sessionId, totalCount, correctCount, incorrectCount, results, session);
    }
    public record QuestionResult(
            Long questionId,
            String content,
            Question.Difficulty difficulty,
            boolean correct,
            int selectedOptionIndex,
            String selectedOption,
            int correctOptionIndex,
            String correctOption
    ) {}
}
