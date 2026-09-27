package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konkuk.memory.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionGenerationPersistenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionGenerationPersistenceService service =
            new QuestionGenerationPersistenceService(null, null, null, objectMapper);

    @Test
    void storesOnlyPhotosReferencedByTheQuestion() throws Exception {
        ObjectNode albumEvidence = objectMapper.createObjectNode();
        albumEvidence.put("albumId", 7L);
        var photos = albumEvidence.putArray("photos");
        for (long photoId = 1; photoId <= 8; photoId++) {
            ObjectNode photo = photos.addObject();
            photo.put("photoId", photoId);
            photo.putObject("aiClues")
                    .putObject("value")
                    .putArray("objects")
                    .add(("사진 " + photoId + " 단서 ").repeat(300));
        }

        String fullAlbumJson = objectMapper.writeValueAsString(albumEvidence);
        String snapshotJson = service.evidenceSnapshot(
                fullAlbumJson,
                List.of(2L, 5L),
                List.of(5L),
                Question.Difficulty.HARD
        );
        var snapshot = objectMapper.readTree(snapshotJson);

        assertThat(snapshot.path("albumId").asLong()).isEqualTo(7L);
        assertThat(snapshot.path("photos")).hasSize(2);
        assertThat(snapshot.path("photos").path(0).path("photoId").asLong()).isEqualTo(2L);
        assertThat(snapshot.path("photos").path(1).path("photoId").asLong()).isEqualTo(5L);
        assertThat(snapshot.path("evidencePhotoIds").toString()).isEqualTo("[2,5]");
        assertThat(snapshot.path("displayPhotoIds").toString()).isEqualTo("[5]");
        assertThat(snapshot.path("difficulty").asText()).isEqualTo("HARD");
        assertThat(snapshotJson.length()).isLessThan(fullAlbumJson.length() / 2);
    }
}