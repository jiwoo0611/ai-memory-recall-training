package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumQuestionReadinessServiceTest {

    @Test
    void acceptsFourDistinctHighQualityScenesAndKeepsOnePhotoPerScene() {
        PhotoRepository photos = mock(PhotoRepository.class);
        List<Photo> albumPhotos = List.of(
                photo(1L, 85, "모닥불 감상", "activity"),
                photo(2L, 70, "모닥불 감상", "activity"),
                photo(3L, 80, "저녁 식사", "food"),
                photo(4L, 75, "산책", "timeline"),
                photo(5L, 90, "랜드마크 관람", "landmark")
        );
        when(photos.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(7L, 3L))
                .thenReturn(albumPhotos);

        var service = new AlbumQuestionReadinessService(photos, new ObjectMapper());
        var readiness = service.requireReady(3L, 7L);

        assertThat(readiness.totalPhotoCount()).isEqualTo(5);
        assertThat(readiness.eligibleSceneCount()).isEqualTo(4);
        assertThat(readiness.eligiblePhotoIds()).containsExactlyInAnyOrder(1L, 3L, 4L, 5L);
    }

    @Test
    void asksForMorePhotosBeforeQuestionGenerationWhenScenesAreInsufficient() {
        PhotoRepository photos = mock(PhotoRepository.class);
        List<Photo> albumPhotos = List.of(
                photo(1L, 85, "모닥불 감상", "activity"),
                photo(2L, 80, "모닥불 감상", "activity"),
                photo(3L, 75, "저녁 식사", "activity"),
                photo(4L, 40, "흐린 사진", "activity")
        );
        when(photos.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(7L, 3L))
                .thenReturn(albumPhotos);

        var service = new AlbumQuestionReadinessService(photos, new ObjectMapper());

        assertThatThrownBy(() -> service.requireReady(3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("전체 4장")
                .hasMessageContaining("대표 장면이 2개")
                .hasMessageContaining("2개 이상 더 추가")
                .hasMessageContaining("음식·음료 사진")
                .hasMessageContaining("장소나 랜드마크");
    }

    private static Photo photo(Long id, int score, String sceneKey, String... topics) {
        Photo photo = mock(Photo.class);
        when(photo.getId()).thenReturn(id);
        String topicValues = String.join("\", \"", topics);
        when(photo.getAiClues()).thenReturn("""
                {
                  "summary": "회상할 수 있는 장면",
                  "questionSuitability": {
                    "score": %d,
                    "quality": "%s",
                    "sceneKey": "%s",
                    "usableTopics": ["%s"],
                    "exclusionReasons": []
                  }
                }
                """.formatted(
                score,
                score >= 60 ? "GOOD" : "POOR",
                sceneKey,
                topicValues
        ));
        return photo;
    }
}
