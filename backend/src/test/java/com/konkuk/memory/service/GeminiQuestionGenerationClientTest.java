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
    void normalizesExactlyThreeQuestionsAndKeepsOwnedEvidenceIds() throws Exception {
        String output = """
                {
                  "questions": [
                    {
                      "content": "이 사진들을 찍던 날 가장 기억에 남는 순간은 무엇인가요?",
                      "type": "EXPERIENCE",
                      "evidencePhotoIds": [11, 12, 999],
                      "verifiable": false,
                      "referenceInformation": ""
                    },
                    {
                      "content": "사진마다 보이는 음식은 당시 어떤 자리와 관련이 있나요?",
                      "type": "PHOTO_CLUE",
                      "evidencePhotoIds": [11, 12],
                      "verifiable": false,
                      "referenceInformation": ""
                    },
                    {
                      "content": "촬영 날짜가 2026년 5월 9일이 맞나요?",
                      "type": "INFORMATION_CHECK",
                      "evidencePhotoIds": [12],
                      "verifiable": true,
                      "referenceInformation": "EXIF 촬영일: 2026-05-09"
                    }
                  ]
                }
                """;

        var questions = client.normalize(output, Set.of(11L, 12L));

        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).evidencePhotoIds()).containsExactly(11L, 12L);
        assertThat(questions.get(0).verifiable()).isFalse();
        assertThat(questions.get(2).type()).isEqualTo(Question.Type.INFORMATION_CHECK);
        assertThat(questions.get(2).verifiable()).isTrue();
        assertThat(questions.get(2).referenceInformation())
                .isEqualTo("EXIF 촬영일: 2026-05-09");
        assertThat(client.generationVersion())
                .isEqualTo("gemini-3.5-flash-lite:album-v1");
    }

    @Test
    void rejectsQuestionWithoutAnOwnedEvidencePhoto() {
        String output = """
                {
                  "questions": [
                    {"content":"질문 1", "type":"EXPERIENCE", "evidencePhotoIds":[999], "verifiable":false, "referenceInformation":""},
                    {"content":"질문 2", "type":"PHOTO_CLUE", "evidencePhotoIds":[11], "verifiable":false, "referenceInformation":""},
                    {"content":"질문 3", "type":"CONTEXT", "evidencePhotoIds":[11], "verifiable":false, "referenceInformation":""}
                  ]
                }
                """;

        assertThatThrownBy(() -> client.normalize(output, Set.of(11L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("응답을 질문으로 처리하지 못했습니다");
    }
}