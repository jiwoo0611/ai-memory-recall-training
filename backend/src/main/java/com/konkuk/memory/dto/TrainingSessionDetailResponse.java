package com.konkuk.memory.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.*;

import java.time.Instant;
import java.util.List;

public record TrainingSessionDetailResponse(Long id, TrainingSessionSummaryResponse.PhotoInfo photo,
                                            TrainingSession.Status status, Instant startedAt, Instant completedAt,
                                            List<QuestionInfo> questions, ResultInfo result) {
    @com.fasterxml.jackson.annotation.JsonProperty("respondedCount")
    public int respondedCount() { return (int) questions.stream().filter(q -> q.answer() != null).count(); }
    @com.fasterxml.jackson.annotation.JsonProperty("questionCount")
    public int questionCount() { return questions.size(); }
    public record QuestionInfo(Long id, int questionOrder, String content, Question.Type type,
                               Question.Format format, Question.Difficulty difficulty, List<String> options, List<Long> displayPhotoIds, AnswerInfo answer) {
        public static QuestionInfo from(Question question, Answer answer, ObjectMapper objectMapper) {
            return new QuestionInfo(
                    question.getId(),
                    question.getQuestionOrder(),
                    question.getContent(),
                    question.getType(),
                    question.getFormat(),
                    readDifficulty(question.getEvidenceSnapshot(), objectMapper),
                    readOptions(question.getOptionsJson(), objectMapper),
                    readDisplayPhotoIds(question.getEvidenceSnapshot(), objectMapper),
                    answer == null ? null : AnswerInfo.from(answer, objectMapper)
            );
        }

        private static Question.Difficulty readDifficulty(String evidenceSnapshot, ObjectMapper objectMapper) {
            try {
                String value = objectMapper.readTree(evidenceSnapshot).path("difficulty").asText("MEDIUM");
                return Question.Difficulty.valueOf(value);
            } catch (Exception ignored) {
                return Question.Difficulty.MEDIUM;
            }
        }
        private static List<Long> readDisplayPhotoIds(String evidenceSnapshot, ObjectMapper objectMapper) {
            if (evidenceSnapshot == null || evidenceSnapshot.isBlank()) {
                return List.of();
            }
            try {
                var node = objectMapper.readTree(evidenceSnapshot).path("displayPhotoIds");
                return node.isArray()
                        ? objectMapper.convertValue(node, new TypeReference<List<Long>>() {})
                        : List.of();
            } catch (Exception ignored) {
                return List.of();
            }
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

    public record AnswerInfo(Long id, Answer.Status status, String content, Instant createdAt,
                             Integer selectedOptionIndex, Boolean correct,
                             Integer correctOptionIndex, String correctOption) {
        public static AnswerInfo from(Answer answer, ObjectMapper mapper) {
            Integer correctIndex = null;
            String correctOption = null;
            if (answer.getCorrect() != null) {
                try {
                    correctIndex = mapper.readTree(answer.getQuestion().getAnswerKey()).path("correctOptionIndex").intValue();
                    correctOption = mapper.readTree(answer.getQuestion().getOptionsJson()).get(correctIndex).asText();
                } catch (Exception ex) { throw new IllegalStateException("Invalid saved quiz snapshot", ex); }
            }
            return new AnswerInfo(answer.getId(), answer.getStatus(), answer.getContent(), answer.getCreatedAt(),
                    answer.getSelectedOptionIndex(), answer.getCorrect(), correctIndex, correctOption);
        }
    }

    public record ResultInfo(Long id, Result.FeedbackStatus feedbackStatus, String summary,
                             int answeredCount, int cannotRecallCount, int mismatchCount,
                             Integer totalCount, Integer correctCount, Integer incorrectCount, String generationVersion) {
        public static ResultInfo from(Result result) {
            return new ResultInfo(result.getId(), result.getFeedbackStatus(), result.getSummary(),
                    result.getAnsweredCount(), result.getCannotRecallCount(), result.getMismatchCount(),
                    result.getTotalCount(), result.getCorrectCount(), result.getIncorrectCount(), result.getGenerationVersion());
        }
    }
}
