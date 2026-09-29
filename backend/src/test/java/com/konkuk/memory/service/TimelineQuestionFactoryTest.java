package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineQuestionFactoryTest {

    private final TimelineQuestionFactory factory =
            new TimelineQuestionFactory(new ObjectMapper());

    @Test
    void createsOneTimelineQuestionFromExifTimesAndAiClues() {
        String evidence = """
                {
                  "albumId": 7,
                  "photos": [
                    {
                      "photoId": 12,
                      "takenAt": {"value": "2026-05-09T12:00:00", "source": "EXIF"},
                      "aiClues": {"value": {"foods": [{"name": "피자", "confidence": "HIGH"}]}}
                    },
                    {
                      "photoId": 11,
                      "takenAt": {"value": "2026-05-09T09:00:00", "source": "EXIF"},
                      "aiClues": {"value": {"activities": ["캠핑 준비"]}}
                    },
                    {
                      "photoId": 13,
                      "takenAt": {"value": "2026-05-09T18:00:00", "source": "EXIF"},
                      "aiClues": {"value": {"activities": ["모닥불 감상"]}}
                    }
                  ]
                }
                """;

        var question = factory.create(evidence).orElseThrow();

        assertThat(question.content()).contains("장면의 흐름");
        assertThat(question.options()).hasSize(4).doesNotHaveDuplicates();
        assertThat(question.difficulty()).isEqualTo(com.konkuk.memory.entity.Question.Difficulty.MEDIUM);
        assertThat(question.options().get(question.correctOptionIndex()))
                .isEqualTo("캠핑 준비 → 피자 → 모닥불 감상");
        assertThat(question.evidencePhotoIds()).containsExactly(11L, 12L, 13L);
        assertThat(question.displayPhotoIds()).isEmpty();
    }

    @Test
    void omitsTimelineWhenFewerThanThreeTimedCluesExist() {
        String evidence = """
                {
                  "photos": [
                    {
                      "photoId": 11,
                      "takenAt": {"value": "2026-05-09T09:00:00"},
                      "aiClues": {"value": {"objects": ["텐트"]}}
                    },
                    {
                      "photoId": 12,
                      "aiClues": {"value": {"foods": [{"name": "피자"}]}}
                    }
                  ]
                }
                """;

        assertThat(factory.create(evidence)).isEmpty();
    }
}
