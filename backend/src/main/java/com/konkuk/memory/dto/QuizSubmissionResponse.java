package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Question;

import java.util.List;

public record QuizSubmissionResponse(
        Long sessionId,
        int totalCount,
        int correctCount,
        int incorrectCount,
        List<QuestionResult> results
) {
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