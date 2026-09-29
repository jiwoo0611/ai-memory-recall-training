package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiPhotoAnalysisClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiPhotoAnalysisClient client =
            new GeminiPhotoAnalysisClient(
                    "test-key",
                    "gemini-3.5-flash-lite",
                    objectMapper
            );

    @Test
    void normalizesStructuredPhotoAnalysisForStorage() throws Exception {
        String modelOutput = """
                {
                  "summary": "식탁 위에 김치찌개가 놓여 있습니다.",
                  "foods": [
                    {"name": "김치찌개", "confidence": "HIGH"},
                    {"name": "반찬", "confidence": "unknown"}
                  ],
                  "landmarks": [
                    {"name": "경복궁", "region": "서울특별시", "confidence": "HIGH"}
                  ],
                  "objects": ["식탁", "그릇"],
                  "scene": "식사 공간",
                  "activities": ["식사"],
                  "peopleCount": 2,
                  "visibleText": [],
                  "questionClues": ["이날 함께 먹은 음식"],
                  "uncertainties": ["정확한 식당 위치는 알 수 없음"],
                  "ignored": "저장하지 않을 값"
                }
                """;

        JsonNode stored = objectMapper.readTree(client.normalize(modelOutput));

        assertThat(stored.path("summary").asText())
                .isEqualTo("식탁 위에 김치찌개가 놓여 있습니다.");
        assertThat(stored.path("foods").path(0).path("name").asText())
                .isEqualTo("김치찌개");
        assertThat(stored.path("foods").path(0).path("confidence").asText())
                .isEqualTo("HIGH");
        assertThat(stored.path("foods").path(1).path("confidence").asText())
                .isEqualTo("LOW");
        assertThat(stored.path("landmarks").path(0).path("name").asText())
                .isEqualTo("경복궁");
        assertThat(stored.path("landmarks").path(0).path("region").asText())
                .isEqualTo("서울특별시");
        assertThat(stored.path("landmarks").path(0).path("confidence").asText())
                .isEqualTo("HIGH");
        assertThat(stored.path("questionClues").path(0).asText())
                .isEqualTo("이날 함께 먹은 음식");
        assertThat(stored.path("provider").asText())
                .isEqualTo("google-gemini");
        assertThat(stored.path("model").asText())
                .isEqualTo("gemini-3.5-flash-lite");
        assertThat(stored.has("analyzedAt")).isTrue();
        assertThat(stored.has("ignored")).isFalse();
    }
}
