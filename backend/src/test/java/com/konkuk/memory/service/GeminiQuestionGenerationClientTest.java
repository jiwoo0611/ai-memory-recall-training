package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiQuestionGenerationClientTest {

    private final GeminiQuestionGenerationClient client =
            new GeminiQuestionGenerationClient(
                    "test-key",
                    "gemini-3.5-flash-lite",
                    new ObjectMapper()
            );

    @Test
    void normalizesVariableMultipleChoiceQuestionsAndKeepsOwnedEvidenceIds() throws Exception {
        String output = """
                {
                  "questions": [
                    {
                      "content": "이 여행에서 먹었던 음식은 무엇인가요?",
                      "type": "PHOTO_CLUE",
                      "options": ["피자", "초밥", "냉면", "비빔밥"],
                      "correctOptionIndex": 0,
                      "evidencePhotoIds": [11, 999],
                      "referenceInformation": "사진 11의 foods에 피자가 기록됨"
                    },
                    {
                      "content": "그날 사진에 남아 있던 상표는 무엇인가요?",
                      "type": "CONTEXT",
                      "options": ["Dewar's", "Nike", "Apple", "Coca-Cola"],
                      "correctOptionIndex": 0,
                      "evidencePhotoIds": [12],
                      "referenceInformation": "사진 12의 visibleText에 Dewar's가 기록됨"
                    }
                  ]
                }
                """;

        var questions = client.normalize(output, Set.of(11L, 12L));

        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).content()).isEqualTo("이 여행에서 먹었던 음식은 무엇인가요?");
        assertThat(questions.get(0).options()).containsExactly("피자", "초밥", "냉면", "비빔밥");
        assertThat(questions.get(0).correctOptionIndex()).isZero();
        assertThat(questions.get(0).evidencePhotoIds()).containsExactly(11L);
        assertThat(questions.get(0).verifiable()).isTrue();
        assertThat(questions.get(1).type()).isEqualTo(Question.Type.CONTEXT);
        assertThat(client.generationVersion())
                .isEqualTo("gemini-3.5-flash-lite:album-recall-mcq-v1");
    }

    @Test
    void rejectsQuestionWithoutFourUniqueOptions() {
        String output = """
                {
                  "questions": [
                    {
                      "content": "이 여행에서 먹었던 음식은 무엇인가요?",
                      "type": "PHOTO_CLUE",
                      "options": ["피자", "피자", "냉면", "비빔밥"],
                      "correctOptionIndex": 0,
                      "evidencePhotoIds": [11],
                      "referenceInformation": "foods 근거"
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> client.normalize(output, Set.of(11L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("응답을 질문으로 처리하지 못했습니다");
    }

    @Test
    void rejectsPeopleCountQuestions() {
        String output = """
                {
                  "questions": [
                    {
                      "content": "사진 속 사람은 몇 명이었나요?",
                      "type": "PHOTO_CLUE",
                      "options": ["1명", "2명", "3명", "4명"],
                      "correctOptionIndex": 1,
                      "evidencePhotoIds": [11],
                      "referenceInformation": "사람 수"
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> client.normalize(output, Set.of(11L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsQuestionWithoutOwnedEvidencePhoto() {
        String output = """
                {
                  "questions": [
                    {
                      "content": "이 여행에서 먹었던 음식은 무엇인가요?",
                      "type": "PHOTO_CLUE",
                      "options": ["피자", "초밥", "냉면", "비빔밥"],
                      "correctOptionIndex": 0,
                      "evidencePhotoIds": [999],
                      "referenceInformation": "foods 근거"
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> client.normalize(output, Set.of(11L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("응답을 질문으로 처리하지 못했습니다");
    }
}
