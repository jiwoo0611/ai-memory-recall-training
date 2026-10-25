package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Question;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LocationQuestionFactory {

    private static final List<String> REGIONS = List.of(
            "서울특별시", "부산광역시", "대구광역시", "인천광역시",
            "광주광역시", "대전광역시", "울산광역시", "세종특별자치시",
            "경기도", "강원특별자치도", "충청북도", "충청남도",
            "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도"
    );

    private final ObjectMapper objectMapper;

    public LocationQuestionFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<GeminiQuestionGenerationClient.GeneratedQuestion> create(String evidenceJson) {
        try {
            JsonNode photos = objectMapper.readTree(evidenceJson).path("photos");
            if (!photos.isArray()) {
                return Optional.empty();
            }

            Map<String, List<Long>> photoIdsByRegion = new LinkedHashMap<>();
            for (JsonNode photo : photos) {
                String region = photo.path("location").path("value").asText("").trim();
                if (!REGIONS.contains(region)) {
                    continue;
                }
                photoIdsByRegion.computeIfAbsent(region, ignored -> new ArrayList<>())
                        .add(photo.path("photoId").asLong());
            }

            if (photoIdsByRegion.size() != 1) {
                return Optional.empty();
            }

            Map.Entry<String, List<Long>> location = photoIdsByRegion.entrySet().iterator().next();
            String correctRegion = location.getKey();
            List<String> options = optionsFor(correctRegion);
            int correctIndex = options.indexOf(correctRegion);

            return Optional.of(new GeminiQuestionGenerationClient.GeneratedQuestion(
                    "이 앨범의 사진들을 촬영한 지역은 어디였나요?",
                    Question.Type.INFORMATION_CHECK,
                    Question.Difficulty.EASY,
                    options,
                    correctIndex,
                    location.getValue().stream().limit(4).toList(),
                    List.of(),
                    true,
                    "사진 GPS 메타데이터에서 확인된 촬영 지역: " + correctRegion,
                    120
            ));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private static List<String> optionsFor(String correctRegion) {
        int regionIndex = REGIONS.indexOf(correctRegion);
        List<String> options = new ArrayList<>();
        for (int offset = 1; options.size() < 3; offset++) {
            String candidate = REGIONS.get(Math.floorMod(regionIndex + offset * 5, REGIONS.size()));
            if (!candidate.equals(correctRegion) && !options.contains(candidate)) {
                options.add(candidate);
            }
        }
        int correctIndex = Math.floorMod(correctRegion.hashCode(), 4);
        options.add(correctIndex, correctRegion);
        return List.copyOf(options);
    }
}
