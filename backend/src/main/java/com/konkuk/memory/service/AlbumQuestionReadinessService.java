package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AlbumQuestionReadinessService {

    static final int MIN_SUITABILITY_SCORE = 60;
    static final int MIN_DISTINCT_SCENES = 6;
    static final int MIN_TOPIC_TYPES = 2;
    private static final String TOPIC_ACTIVITY = "ACTIVITY";
    private static final String TOPIC_FOOD_DRINK = "FOOD_DRINK";
    private static final String TOPIC_PLACE = "PLACE";
    private static final String TOPIC_TIMELINE = "TIMELINE";

    private final PhotoRepository photoRepository;
    private final ObjectMapper objectMapper;

    public AlbumQuestionReadinessService(
            PhotoRepository photoRepository,
            ObjectMapper objectMapper
    ) {
        this.photoRepository = photoRepository;
        this.objectMapper = objectMapper;
    }

    public Readiness requireReady(Long userId, Long albumId) {
        List<Photo> photos = photoRepository
                .findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(albumId, userId);

        Map<String, Candidate> bestByScene = new LinkedHashMap<>();
        Set<String> availableTopics = new LinkedHashSet<>();
        for (Photo photo : photos) {
            Candidate candidate = assess(photo);
            if (candidate == null || candidate.score() < MIN_SUITABILITY_SCORE) {
                continue;
            }
            availableTopics.addAll(candidate.topics());
            bestByScene.merge(
                    candidate.sceneKey(),
                    candidate,
                    (current, replacement) -> replacement.score() > current.score()
                            ? replacement
                            : current
            );
        }

        List<Candidate> representatives = bestByScene.values().stream()
                .sorted(Comparator.comparingLong(Candidate::photoId))
                .toList();
        int sceneCount = representatives.size();
        int additionalRequired = Math.max(0, MIN_DISTINCT_SCENES - sceneCount);

        if (additionalRequired > 0) {
            String recommendation = buildRecommendation(availableTopics, additionalRequired);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_PHOTO_QUALITY_INSUFFICIENT",
                    "전체 " + photos.size() + "장 중 질문에 사용할 만한 서로 다른 대표 장면이 "
                            + sceneCount + "개입니다. "
                            + "회상 퀴즈를 만들려면 활동·음식·장소처럼 서로 다른 장면의 사진을 "
                            + additionalRequired + "개 이상 더 추가해주세요. "
                            + recommendation
            );
        }
        long primaryTopicCount = availableTopics.stream()
                .filter(topic -> !TOPIC_TIMELINE.equals(topic))
                .count();
        if (primaryTopicCount < MIN_TOPIC_TYPES) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_PHOTO_DIVERSITY_INSUFFICIENT",
                    "대표 장면은 " + sceneCount + "개지만 질문 주제가 한 종류에 집중되어 있습니다. "
                            + buildRecommendation(availableTopics, 2)
            );
        }

        Set<Long> eligiblePhotoIds = representatives.stream()
                .map(Candidate::photoId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new Readiness(
                photos.size(),
                sceneCount,
                Set.copyOf(eligiblePhotoIds)
        );
    }

    private Candidate assess(Photo photo) {
        String raw = photo.getAiClues();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode clues = objectMapper.readTree(raw);
            JsonNode suitability = clues.path("questionSuitability");
            int score = suitability.has("score")
                    ? Math.max(0, Math.min(100, suitability.path("score").asInt(0)))
                    : fallbackScore(clues);

            if (photo.getMetadataTakenAt() != null) {
                score = Math.min(100, score + 5);
            }
            if (!clean(photo.getMetadataLocation()).isBlank()) {
                score = Math.min(100, score + 5);
            }
            String quality = clean(suitability.path("quality").asText(""));
            if ("POOR".equalsIgnoreCase(quality)) {
                score = Math.min(score, MIN_SUITABILITY_SCORE - 1);
            }

            String sceneKey = clean(suitability.path("sceneKey").asText(""));
            if (sceneKey.isBlank()) {
                sceneKey = fallbackSceneKey(clues);
            }
            String normalizedKey = normalizeSceneKey(sceneKey);
            if (normalizedKey.isBlank()) {
                normalizedKey = "photo-" + photo.getId();
            }
            return new Candidate(
                    photo.getId(),
                    score,
                    normalizedKey,
                    extractTopics(clues, suitability)
            );
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static int fallbackScore(JsonNode clues) {
        int score = clean(clues.path("summary").asText("")).isBlank() ? 0 : 10;
        if (hasConfidentItem(clues.path("foods"))) score += 25;
        if (hasValues(clues.path("activities"))) score += 25;
        if (hasValues(clues.path("questionClues"))) score += 20;
        if (hasValues(clues.path("objects"))) score += 10;
        if (hasHighConfidenceItem(clues.path("landmarks"))) score += 25;
        if (clues.path("uncertainties").size() >= 3) score -= 10;
        return Math.max(0, Math.min(100, score));
    }

    private static Set<String> extractTopics(JsonNode clues, JsonNode suitability) {
        Set<String> topics = new LinkedHashSet<>();
        JsonNode usableTopics = suitability.path("usableTopics");
        if (usableTopics.isArray()) {
            usableTopics.forEach(value -> {
                String topic = clean(value.asText("")).toLowerCase(Locale.ROOT);
                if (topic.contains("activity") || topic.contains("활동")) {
                    topics.add(TOPIC_ACTIVITY);
                }
                if (topic.contains("food") || topic.contains("drink")
                        || topic.contains("음식") || topic.contains("음료")) {
                    topics.add(TOPIC_FOOD_DRINK);
                }
                if (topic.contains("landmark") || topic.contains("place")
                        || topic.contains("location") || topic.contains("장소")) {
                    topics.add(TOPIC_PLACE);
                }
                if (topic.contains("timeline") || topic.contains("시간")) {
                    topics.add(TOPIC_TIMELINE);
                }
            });
        }

        if (hasValues(clues.path("activities"))) topics.add(TOPIC_ACTIVITY);
        if (hasConfidentItem(clues.path("foods"))) topics.add(TOPIC_FOOD_DRINK);
        if (hasHighConfidenceItem(clues.path("landmarks"))) topics.add(TOPIC_PLACE);
        return Set.copyOf(topics);
    }

    private static String buildRecommendation(Set<String> availableTopics, int additionalRequired) {
        Map<String, String> recommendations = new LinkedHashMap<>();
        recommendations.put(TOPIC_ACTIVITY, "무엇을 했는지 보이는 활동 사진");
        recommendations.put(TOPIC_FOOD_DRINK, "먹거나 마신 것이 보이는 음식·음료 사진");
        recommendations.put(TOPIC_PLACE, "방문한 장소나 랜드마크가 보이는 사진");
        recommendations.put(TOPIC_TIMELINE, "준비·이동·마무리처럼 다른 시간대의 사진");

        List<String> missing = recommendations.entrySet().stream()
                .filter(entry -> !availableTopics.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .limit(Math.min(3, Math.max(1, additionalRequired)))
                .toList();

        if (missing.isEmpty()) {
            return "현재 사진과 다른 장면의 사진을 추가해주세요.";
        }
        return "추천: " + String.join(", ", missing) + "을 추가해주세요.";
    }

    private static String fallbackSceneKey(JsonNode clues) {
        String value = firstNamedItem(clues.path("landmarks"), true);
        if (!value.isBlank()) return value;
        value = firstText(clues.path("activities"));
        if (!value.isBlank()) return value;
        value = firstNamedItem(clues.path("foods"), false);
        if (!value.isBlank()) return value;
        value = firstText(clues.path("questionClues"));
        if (!value.isBlank()) return value;
        value = firstText(clues.path("objects"));
        if (!value.isBlank()) return value;
        value = clean(clues.path("scene").asText(""));
        if (!value.isBlank()) return value;
        return clean(clues.path("summary").asText(""));
    }

    private static boolean hasConfidentItem(JsonNode values) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) {
            if (!clean(value.path("name").asText("")).isBlank()
                    && !"LOW".equalsIgnoreCase(value.path("confidence").asText("LOW"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHighConfidenceItem(JsonNode values) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) {
            if (!clean(value.path("name").asText("")).isBlank()
                    && "HIGH".equalsIgnoreCase(value.path("confidence").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValues(JsonNode values) {
        return values.isArray() && values.size() > 0;
    }

    private static String firstNamedItem(JsonNode values, boolean highOnly) {
        if (!values.isArray()) return "";
        for (JsonNode value : values) {
            String confidence = value.path("confidence").asText("LOW");
            String name = clean(value.path("name").asText(""));
            if (!name.isBlank() && (!highOnly || "HIGH".equalsIgnoreCase(confidence))) {
                return name;
            }
        }
        return "";
    }

    private static String firstText(JsonNode values) {
        if (!values.isArray()) return "";
        for (JsonNode value : values) {
            String text = clean(value.asText(""));
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private static String normalizeSceneKey(String value) {
        String normalized = clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^가-힣a-z0-9]", "");
        return normalized
                .replace("불멍", "모닥불")
                .replace("불꽃", "모닥불")
                .replace("캠프파이어", "모닥불")
                .replace("술마시기", "음료")
                .replace("술마시는", "음료")
                .replace("음료마시기", "음료")
                .replace("식사하기", "식사")
                .replace("식사하는", "식사")
                .replace("관람", "")
                .replace("방문", "")
                .replace("감상", "")
                .replace("하는장면", "")
                .replace("장면", "")
                .replace("모습", "")
                .replace("하기", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record Candidate(
            Long photoId,
            int score,
            String sceneKey,
            Set<String> topics
    ) {}

    public record Readiness(
            int totalPhotoCount,
            int eligibleSceneCount,
            Set<Long> eligiblePhotoIds
    ) {}
}
