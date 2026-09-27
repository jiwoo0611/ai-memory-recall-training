package com.konkuk.memory.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.*;

import java.time.Instant;
import java.util.List;

public record TrainingSessionDetailResponse(Long id, TrainingSessionSummaryResponse.PhotoInfo photo,
                                            TrainingSession.Status status, Instant startedAt, Instant completedAt,
                                            List<QuestionInfo> questions, ResultInfo result) {
    public record QuestionInfo(Long id, int questionOrder, String content, Question.Type type,
                               Question.Format format, List<String> options, AnswerInfo answer) {
        public static QuestionInfo from(Question question, Answer answer, ObjectMapper objectMapper) {
            return new QuestionInfo(
                    question.getId(),
                    question.getQuestionOrder(),
                    question.getContent(),
                    question.getType(),
                    question.getFormat(),
                    readOptions(question.getOptionsJson(), objectMapper),
                    answer == null ? null : AnswerInfo.from(answer)
            );
        }

        private static List<String> readOptions(String optionsJson, ObjectMapper objectMapper) {
            if (optionsJson == null || optionsJson.isBlank()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(optionsJson, new TypeReference<>() {});
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }

    public record AnswerInfo(Long id, Answer.Status status, String content, Instant createdAt) {
        public static AnswerInfo from(Answer answer) {
            return new AnswerInfo(answer.getId(), answer.getStatus(), answer.getContent(), answer.getCreatedAt());
        }
    }

    public record ResultInfo(Long id, Result.FeedbackStatus feedbackStatus, String summary,
                             int answeredCount, int cannotRecallCount, int mismatchCount) {
        public static ResultInfo from(Result result) {
            return new ResultInfo(result.getId(), result.getFeedbackStatus(), result.getSummary(),
                    result.getAnsweredCount(), result.getCannotRecallCount(), result.getMismatchCount());
        }
    }
}
