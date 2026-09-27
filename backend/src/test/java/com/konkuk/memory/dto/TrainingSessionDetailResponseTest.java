package com.konkuk.memory.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingSessionDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesOptionsWithoutExposingAnswerKey() throws Exception {
        Question question = new Question(
                null,
                1,
                "이때 먹었던 음식은 무엇인가요?",
                Question.Type.PHOTO_CLUE,
                "{\"displayPhotoIds\":[11,12],\"difficulty\":\"HARD\"}",
                true,
                "foods에 피자",
                "test",
                Question.Format.MULTIPLE_CHOICE,
                "[\"피자\",\"초밥\",\"냉면\",\"비빔밥\"]",
                "{\"correctOptionIndex\":0}"
        );

        var response = TrainingSessionDetailResponse.QuestionInfo.from(
                question,
                null,
                objectMapper
        );
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.options()).containsExactly("피자", "초밥", "냉면", "비빔밥");
        assertThat(response.displayPhotoIds()).containsExactly(11L, 12L);
        assertThat(response.difficulty()).isEqualTo(Question.Difficulty.HARD);
        assertThat(json).doesNotContain("answerKey", "correctOptionIndex");
    }
}
