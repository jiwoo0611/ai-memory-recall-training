package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.entity.Result;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;

/** Uses only persisted answers and the immutable per-session question snapshots. */
@Component
public class QuizSummaryGenerator {
    private final ObjectMapper mapper;
    public QuizSummaryGenerator(ObjectMapper mapper) { this.mapper = mapper; }

    public String generate(Result result, List<Answer> answers) {
        StringBuilder text = new StringBuilder("총 %d개 질문에 답했고, %d개에 정답을 선택했습니다."
                .formatted(result.getAnsweredCount(), result.getCorrectCount()));
        answers.stream().sorted(Comparator.comparingInt(a -> a.getQuestion().getQuestionOrder()))
                .filter(a -> Boolean.FALSE.equals(a.getCorrect())).forEach(answer -> {
                    try {
                        var question = answer.getQuestion();
                        int correct = mapper.readTree(question.getAnswerKey()).path("correctOptionIndex").asInt(-1);
                        String option = mapper.readTree(question.getOptionsJson()).get(correct).asText();
                        text.append("\n%d번 질문: 선택한 답 ‘%s’, 저장된 정답 ‘%s’."
                                .formatted(question.getQuestionOrder(), answer.getContent(), option));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Saved question snapshot is invalid", ex);
                    }
                });
        if (result.getIncorrectCount() > 0) text.append("\n사진과 질문을 다시 확인해보세요.");
        return text.toString();
    }
}
