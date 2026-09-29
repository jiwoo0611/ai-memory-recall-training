package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationQuestionFactoryTest {

    private final LocationQuestionFactory factory = new LocationQuestionFactory(new ObjectMapper());

    @Test
    void createsEasyRecallQuestionFromSharedGpsRegion() {
        String evidence = """
                {
                  "photos": [
                    {"photoId": 11, "location": {"value": "경기도", "source": "EXIF"}},
                    {"photoId": 12, "location": {"value": "경기도", "source": "EXIF"}}
                  ]
                }
                """;

        var question = factory.create(evidence).orElseThrow();

        assertThat(question.content()).contains("촬영한 지역");
        assertThat(question.type()).isEqualTo(Question.Type.INFORMATION_CHECK);
        assertThat(question.difficulty()).isEqualTo(Question.Difficulty.EASY);
        assertThat(question.options()).hasSize(4).doesNotHaveDuplicates().contains("경기도");
        assertThat(question.options().get(question.correctOptionIndex())).isEqualTo("경기도");
        assertThat(question.evidencePhotoIds()).containsExactly(11L, 12L);
        assertThat(question.displayPhotoIds()).isEmpty();
    }

    @Test
    void omitsLocationQuestionForCoordinatesOrMixedRegions() {
        String coordinates = """
                {"photos":[{"photoId":11,"location":{"value":"37.1,127.1"}}]}
                """;
        String mixed = """
                {"photos":[
                  {"photoId":11,"location":{"value":"경기도"}},
                  {"photoId":12,"location":{"value":"서울특별시"}}
                ]}
                """;

        assertThat(factory.create(coordinates)).isEmpty();
        assertThat(factory.create(mixed)).isEmpty();
    }
}
