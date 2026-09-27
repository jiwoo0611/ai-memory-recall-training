package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AlbumQuestionContextService {

    private static final int MAX_AI_CLUES_LENGTH = 4_000;

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
        albumEvidence.put("albumTitle", album.getTitle());
        var photoEvidence = albumEvidence.putArray("photos");
        Set<Long> photoIds = new LinkedHashSet<>();
        boolean hasMeaningfulEvidence = false;

        for (Photo photo : photos) {
            photoIds.add(photo.getId());
            ObjectNode item = photoEvidence.addObject();
            item.put("photoId", photo.getId());

            if (photo.getConfirmedTakenOn() != null) {
                addSourcedValue(item, "takenAt", photo.getConfirmedTakenOn().toString(), "USER_CONFIRMED");
                hasMeaningfulEvidence = true;
            } else if (photo.getMetadataTakenAt() != null) {
                addSourcedValue(item, "takenAt", photo.getMetadataTakenAt().toString(), "EXIF");
                hasMeaningfulEvidence = true;
            }

            String confirmedLocation = clean(photo.getConfirmedLocation());
            String metadataLocation = clean(photo.getMetadataLocation());
            if (!confirmedLocation.isBlank()) {
                addSourcedValue(item, "location", confirmedLocation, "USER_CONFIRMED");
                hasMeaningfulEvidence = true;
            } else if (!metadataLocation.isBlank()) {
                addSourcedValue(item, "location", metadataLocation, "EXIF");
                hasMeaningfulEvidence = true;
            }

            String aiClues = clean(photo.getAiClues());
            if (!aiClues.isBlank()) {
                ObjectNode inferred = item.putObject("aiClues");
                inferred.put("source", "AI_INFERRED");
                inferred.set("value", parseAiClues(aiClues));
                hasMeaningfulEvidence = true;
            }
        }

        if (!hasMeaningfulEvidence) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_EVIDENCE_NOT_READY",
                    "질문에 사용할 정보가 없습니다. 사진을 AI 분석하거나 날짜·장소를 확인해주세요."
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

    private JsonNode parseAiClues(String value) {
        String bounded = value.length() <= MAX_AI_CLUES_LENGTH
                ? value
                : value.substring(0, MAX_AI_CLUES_LENGTH);
        try {
            return objectMapper.readTree(bounded);
        } catch (JsonProcessingException ex) {
            return objectMapper.getNodeFactory().textNode(bounded);
        }
    }

    private static void addSourcedValue(
            ObjectNode target,
            String field,
            String value,
            String source
    ) {
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