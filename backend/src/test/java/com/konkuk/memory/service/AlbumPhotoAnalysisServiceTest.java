package com.konkuk.memory.service;

import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumPhotoAnalysisServiceTest {

    private final AlbumRepository albumRepository = mock(AlbumRepository.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final AlbumPhotoAnalysisService service =
            new AlbumPhotoAnalysisService(albumRepository, photoRepository);

    @Test
    void rejectsQuestionGenerationUntilEveryPhotoHasAiClues() {
        Album album = mock(Album.class);
        Photo analyzed = mock(Photo.class);
        Photo missing = mock(Photo.class);
        when(albumRepository.findByIdAndUser_Id(7L, 2L)).thenReturn(Optional.of(album));
        when(photoRepository.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(7L, 2L))
                .thenReturn(List.of(analyzed, missing));
        when(analyzed.getAiClues()).thenReturn("{\"summary\":\"분석됨\"}");
        when(missing.getAiClues()).thenReturn(null);

        assertThatThrownBy(() -> service.ensureComplete(2L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AI 분석을 모두 완료");
    }

    @Test
    void acceptsAlbumWhenEveryPhotoIsAlreadyAnalyzed() {
        Album album = mock(Album.class);
        Photo first = mock(Photo.class);
        Photo second = mock(Photo.class);
        when(albumRepository.findByIdAndUser_Id(7L, 2L)).thenReturn(Optional.of(album));
        when(photoRepository.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(7L, 2L))
                .thenReturn(List.of(first, second));
        when(first.getAiClues()).thenReturn("{\"summary\":\"첫 사진\"}");
        when(second.getAiClues()).thenReturn("{\"summary\":\"둘째 사진\"}");

        assertThatCode(() -> service.ensureComplete(2L, 7L)).doesNotThrowAnyException();
    }
}