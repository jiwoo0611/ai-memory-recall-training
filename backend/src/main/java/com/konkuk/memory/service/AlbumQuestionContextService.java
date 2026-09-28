package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AlbumQuestionContextService {

    private static final int MAX_AI_CLUES_LENGTH = 4_000;
    private static final Set<String> ALLOWED_ARRAY_FIELDS =
            Set.of("objects", "activities", "visibleText", "questionClues");
    private static final List<String> PEOPLE_TERMS = List.of(
            "사람", "인물", "남성", "여성", "아이", "어린이", "친구", "가족",
            "부부", "연인", "person", "people", "man", "woman", "boy", "girl"
    );

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final ObjectMapper objectMapper;

    public AlbumQuestionContextService(
            AlbumRepository albumRepository,
            PhotoRepository photoRepository,
            ObjectMapper objectMapper
    ) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.objectMapper = objectMapper;
    }

    public AlbumQuestionContext build(Long userId, Long albumId) {
        Album album = albumRepository.findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));
        List<Photo> photos = photoRepository
                .findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(albumId, userId);

        if (photos.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_PHOTOS_REQUIRED",
                    "질문을 생성하려면 앨범에 사진을 먼저 추가해주세요."
            );
        }

        ObjectNode albumEvidence = objectMapper.createObjectNode();
        albumEvidence.put("albumId", album.getId());
        var photoEvidence = albumEvidence.putArray("photos");
        Set<Long> photoIds = new LinkedHashSet<>();
        boolean hasMeaningfulEvidence = false;

        for (Photo photo : photos) {
            photoIds.add(photo.getId());
            ObjectNode item = photoEvidence.addObject();
            item.put("photoId", photo.getId());

            if (photo.getMetadataTakenAt() != null) {
                addSourcedValue(item, "takenAt", photo.getMetadataTakenAt().toString(), "EXIF");
                hasMeaningfulEvidence = true;
            }

            String metadataLocation = clean(photo.getMetadataLocation());
            if (!metadataLocation.isBlank()) {
                addSourcedValue(item, "location", metadataLocation, "EXIF");
                hasMeaningfulEvidence = true;
            }

            JsonNode sanitizedAiClues = sanitizeAiClues(photo.getAiClues());
            if (!sanitizedAiClues.isEmpty()) {
                ObjectNode inferred = item.putObject("aiClues");
                inferred.put("source", "AI_INFERRED");
                inferred.set("value", sanitizedAiClues);
                hasMeaningfulEvidence = true;
            }
        }

        if (!hasMeaningfulEvidence) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_EVIDENCE_NOT_READY",
                    "사진에서 질문에 사용할 단서를 찾지 못했습니다."
            );
        }

        try {
            return new AlbumQuestionContext(
                    album.getId(),
                    photos.get(0).getId(),
                    Set.copyOf(photoIds),
                    objectMapper.writeValueAsString(albumEvidence)
            );
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "QUESTION_EVIDENCE_FAILED",
                    "질문 생성 자료를 준비하지 못했습니다."
            );
        }
    }

    private ObjectNode sanitizeAiClues(String rawValue) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        String value = clean(rawValue);
        if (value.isBlank()) {
            return sanitized;
        }

        String bounded = value.length() <= MAX_AI_CLUES_LENGTH
                ? value
                : value.substring(0, MAX_AI_CLUES_LENGTH);
        try {
            JsonNode source = objectMapper.readTree(bounded);

            String summary = clean(source.path("summary").asText(""));
            if (!summary.isBlank() && !mentionsPeople(summary)) {
                sanitized.put("contextDescription", summary);
            }
            ArrayNode foods = sanitized.putArray("foods");
            JsonNode sourceFoods = source.path("foods");
            if (sourceFoods.isArray()) {
                sourceFoods.forEach(food -> {
                    String name = clean(food.path("name").asText(""));
                    String confidence = clean(food.path("confidence").asText("LOW")).toUpperCase(Locale.ROOT);
                    if (!name.isBlank() && !mentionsPeople(name) && !"LOW".equals(confidence)) {
                        ObjectNode item = foods.addObject();
                        item.put("name", name);
                        item.put("confidence", confidence);
                    }
                });
            }
            if (foods.isEmpty()) {
                sanitized.remove("foods");
            }

            for (String field : ALLOWED_ARRAY_FIELDS) {
                ArrayNode values = sanitized.putArray(field);
                JsonNode input = source.path(field);
                if (input.isArray()) {
                    input.forEach(node -> {
                        String text = clean(node.asText(""));
                        if (!text.isBlank() && !mentionsPeople(text)) {
                            values.add(text);
                        }
                    });
                }
                if (values.isEmpty()) {
                    sanitized.remove(field);
                }
            }
            return sanitized;
        } catch (JsonProcessingException ex) {
            return sanitized;
        }
    }

    private static boolean mentionsPeople(String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        return PEOPLE_TERMS.stream().anyMatch(lowered::contains);
    }

    private static void addSourcedValue(ObjectNode target, String field, String value, String source) {
        ObjectNode sourcedValue = target.putObject(field);
        sourcedValue.put("value", value);
        sourcedValue.put("source", source);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record AlbumQuestionContext(
            Long albumId,
            Long anchorPhotoId,
            Set<Long> photoIds,
            String evidenceJson
    ) {}
}
