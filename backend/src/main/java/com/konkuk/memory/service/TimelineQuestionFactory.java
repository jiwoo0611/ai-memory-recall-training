package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Question;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TimelineQuestionFactory {

    private final ObjectMapper objectMapper;

    public TimelineQuestionFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<GeminiQuestionGenerationClient.GeneratedQuestion> create(String evidenceJson) {
        try {
            JsonNode photos = objectMapper.readTree(evidenceJson).path("photos");
            if (!photos.isArray()) {
                return Optional.empty();
            }

            List<TimedClue> candidates = new ArrayList<>();
            Set<String> usedLabels = new LinkedHashSet<>();
            for (JsonNode photo : photos) {
                String takenAt = photo.path("takenAt").path("value").asText("");
                String label = firstClueLabel(photo.path("aiClues").path("value"));
                if (takenAt.isBlank() || label.isBlank() || usedLabels.contains(label)) {
                    continue;
                }
                try {
                    candidates.add(new TimedClue(
                            photo.path("photoId").asLong(),
                            LocalDateTime.parse(takenAt),
                            label
                    ));
                    usedLabels.add(label);
                } catch (RuntimeException ignored) {
                    // Invalid EXIF date is omitted from the timeline question.
                }
            }

            candidates.sort(Comparator.comparing(TimedClue::takenAt));
            if (candidates.size() < 3) {
                return Optional.empty();
            }
            List<TimedClue> selected = List.copyOf(candidates.subList(0, Math.min(4, candidates.size())));
            List<String> correct = selected.stream().map(TimedClue::label).toList();
            List<List<String>> sequences = new ArrayList<>();
            sequences.add(correct);
            sequences.add(reverse(correct));
            sequences.add(rotate(correct, 1));
            sequences.add(swapFirstTwo(correct));

            List<String> options = sequences.stream()
                    .map(sequence -> String.join(" → ", sequence))
                    .distinct()
                    .toList();
            if (options.size() != 4) {
                return Optional.empty();
            }

            int correctIndex = Math.floorMod(selected.get(0).photoId().intValue(), 4);
            List<String> orderedOptions = new ArrayList<>(options);
            String correctOption = orderedOptions.remove(0);
            orderedOptions.add(correctIndex, correctOption);

            String reference = selected.stream()
                    .map(item -> item.label() + ": " + item.takenAt())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");

            return Optional.of(new GeminiQuestionGenerationClient.GeneratedQuestion(
                    "이 앨범의 기억을 촬영한 시간 순서대로 나열한 것은 무엇인가요?",
                    Question.Type.INFORMATION_CHECK,
                    Question.Difficulty.HARD,
                    List.copyOf(orderedOptions),
                    correctIndex,
                    selected.stream().map(TimedClue::photoId).toList(),
                    selected.stream().map(TimedClue::photoId).sorted().toList(),
                    true,
                    reference
            ));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private static String firstClueLabel(JsonNode clues) {
        JsonNode foods = clues.path("foods");
        if (foods.isArray()) {
            for (JsonNode food : foods) {
                String name = food.path("name").asText("").trim();
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        for (String field : List.of("activities", "visibleText", "objects", "questionClues")) {
            JsonNode values = clues.path(field);
            if (values.isArray()) {
                for (JsonNode value : values) {
                    String text = value.asText("").trim();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    private static List<String> reverse(List<String> source) {
        List<String> result = new ArrayList<>(source);
        java.util.Collections.reverse(result);
        return result;
    }

    private static List<String> rotate(List<String> source, int distance) {
        List<String> result = new ArrayList<>(source);
        java.util.Collections.rotate(result, distance);
        return result;
    }

    private static List<String> swapFirstTwo(List<String> source) {
        List<String> result = new ArrayList<>(source);
        java.util.Collections.swap(result, 0, 1);
        return result;
    }

    private record TimedClue(Long photoId, LocalDateTime takenAt, String label) {}
}
