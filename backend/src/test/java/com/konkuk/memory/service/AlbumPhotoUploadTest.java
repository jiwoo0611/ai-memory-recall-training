package com.konkuk.memory.service;

import com.konkuk.memory.controller.PhotoUploadController;
import com.konkuk.memory.dto.PhotoMetadata;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlbumPhotoUploadTest {
    final AlbumRepository albums = mock(AlbumRepository.class);
    final PhotoRepository photos = mock(PhotoRepository.class);
    final ImageValidationService validation = mock(ImageValidationService.class);
    final PhotoMetadataService metadata = mock(PhotoMetadataService.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final User owner = new User("owner@example.com", "password", "소유자");
    final Album first = new Album(owner, "부산 봄 여행"), second = new Album(owner, "부산 가을 여행");
    final MockMultipartFile file = new MockMultipartFile("files", "trip.jpg", "image/jpeg", new byte[]{1});
    PhotoUploadService service;
    @BeforeEach void setup() {
        service = new PhotoUploadService(albums, photos, validation, metadata, storage);
        org.springframework.test.util.ReflectionTestUtils.setField(first, "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(second, "id", 20L);
    }
    @Test void sameLocationStaysInExplicitlySelectedAlbumsAndKeepsMetadata() throws Exception {
        when(albums.findByIdAndUser_Id(10L,1L)).thenReturn(Optional.of(first));
        when(albums.findByIdAndUser_Id(20L,1L)).thenReturn(Optional.of(second));
        var taken = LocalDateTime.of(2026,5,1,12,0);
        when(metadata.extract(file)).thenReturn(new PhotoMetadata(taken,"부산",1));
        when(storage.store(file,1L,10L,".jpg")).thenReturn("first.jpg");
        when(storage.store(file,1L,20L,".jpg")).thenReturn("second.jpg");
        when(photos.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service.upload(1L,10L,List.of(file));
        service.upload(1L,20L,List.of(file));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Photo>> saved = ArgumentCaptor.forClass(List.class);
        verify(photos,times(2)).saveAll(saved.capture());
        assertThat(saved.getAllValues().get(0).get(0).getAlbum()).isSameAs(first);
        assertThat(saved.getAllValues().get(1).get(0).getAlbum()).isSameAs(second);
        for (var batch : saved.getAllValues()) {
            assertThat(batch.get(0).getMetadataLocation()).isEqualTo("부산");
            assertThat(batch.get(0).getMetadataTakenAt()).isEqualTo(taken);
        }
        verify(albums,never()).save(any());
        verify(albums,never()).findByUser_IdAndTitle(any(),any());
    }
    @Test void missingOrForeignAlbumIsRejectedBeforeAnyFileIsStored() {
        when(albums.findByIdAndUser_Id(20L,1L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.upload(1L,20L,List.of(file)))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(404));
        verifyNoInteractions(storage,metadata,validation,photos);
    }
    @Test void absentGpsStillUsesSelectedAlbum() throws Exception {
        when(albums.findByIdAndUser_Id(10L,1L)).thenReturn(Optional.of(first));
        when(metadata.extract(file)).thenReturn(new PhotoMetadata(null,null,null));
        when(photos.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service.upload(1L,10L,List.of(file));
        verify(storage).store(file,1L,10L,".jpg");
        verify(albums,never()).save(any());
    }
    @Test void controllerPassesAuthenticatedOwnerAndPathAlbum() {
        var uploads=mock(PhotoUploadService.class);
        var jwt=Jwt.withTokenValue("test").header("alg","none").subject("1").build();
        when(uploads.upload(1L,20L,List.of(file))).thenReturn(List.of(8L));
        assertThat(new PhotoUploadController(uploads).uploadPhotos(jwt,20L,List.of(file))).containsExactly(8L);
    }
}
