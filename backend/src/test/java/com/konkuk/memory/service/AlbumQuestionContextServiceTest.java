package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumQuestionContextServiceTest {

    @Test
    void usesOnlyExifAndSanitizedAiClues() throws Exception {
        AlbumRepository albums = mock(AlbumRepository.class);
        PhotoRepository photos = mock(PhotoRepository.class);
        Album album = mock(Album.class);
        Photo photo = mock(Photo.class);

        when(album.getId()).thenReturn(7L);
        when(album.getTitle()).thenReturn("사용자 입력 앨범명");
        when(photo.getId()).thenReturn(11L);
        when(photo.getMetadataTakenAt()).thenReturn(LocalDateTime.parse("2026-05-09T09:00:00"));
        when(photo.getMetadataLocation()).thenReturn("37.1,127.1");
        when(photo.getConfirmedTakenOn()).thenReturn(LocalDate.parse("2026-05-10"));
        when(photo.getConfirmedLocation()).thenReturn("사용자 확인 장소");
        when(photo.getAiClues()).thenReturn("""
                {
                  "summary": "친구 두 명이 야외에 있음",
                  "scene": "야외",
                  "peopleCount": 2,
                  "foods": [
                    {"name": "피자", "confidence": "HIGH"},
                    {"name": "스튜", "confidence": "LOW"}
                  ],
                  "objects": ["텐트"],
                  "activities": ["캠핑"],
                  "visibleText": ["Dewar's"],
                  "questionClues": ["친구와 함께 있음", "모닥불"]
                }
                """);
        when(albums.findByIdAndUser_Id(7L, 3L)).thenReturn(Optional.of(album));
        when(photos.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(7L, 3L))
                .thenReturn(List.of(photo));

        var service = new AlbumQuestionContextService(albums, photos, new ObjectMapper());
        var context = service.build(3L, 7L);
        String evidence = context.evidenceJson();

        assertThat(evidence)
                .contains("2026-05-09T09:00", "37.1,127.1", "피자", "텐트", "캠핑", "Dewar's", "모닥불")
                .doesNotContain("사용자 입력 앨범명", "2026-05-10", "사용자 확인 장소",
                        "peopleCount", "summary", "scene", "스튜", "친구");
    }
}
