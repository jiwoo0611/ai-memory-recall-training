package com.konkuk.memory.service;

import com.konkuk.memory.entity.Answer;
import org.springframework.stereotype.Component;
import java.util.List;

/** Extractive feedback: preserves the user's words without inferring events or cognitive ability. */
@Component
public class RecallSummaryGenerator {
    public String generate(List<Answer> answers) {
        var recalled = answers.stream().filter(a -> a.getStatus() == Answer.Status.ANSWERED)
                .sorted(java.util.Comparator.comparingInt(a -> a.getQuestion().getQuestionOrder()))
                .limit(8)
                .map(a -> "질문 " + a.getQuestion().getQuestionOrder() + "에 남긴 답변: “" + excerpt(a.getContent()) + "”")
                .toList();
        return recalled.isEmpty() ? "이번 훈련에는 서술한 답변이 없습니다. 선택한 상태는 질문별 기록에서 확인할 수 있습니다."
                : "이번 훈련에서 직접 남긴 회상 기록입니다.\n" + String.join("\n", recalled)
                + "\n전체 내용은 질문별 답변에서 확인할 수 있습니다.";
    }
    private String excerpt(String value) {
        int count = value.codePointCount(0, value.length());
        return count <= 180 ? value : value.substring(0, value.offsetByCodePoints(0, 180)) + "…";
    }
}
