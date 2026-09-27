package com.konkuk.memory.service;

import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlbumPhotoAnalysisService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAnalysisService photoAnalysisService;

    public AlbumPhotoAnalysisService(
            AlbumRepository albumRepository,
            PhotoRepository photoRepository,
            PhotoAnalysisService photoAnalysisService
    ) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.photoAnalysisService = photoAnalysisService;
    }

    public void analyzeMissing(Long userId, Long albumId) {
        albumRepository.findByIdAndUser_Id(albumId, userId)
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

        for (Photo photo : photos) {
            if (photo.getAiClues() == null || photo.getAiClues().isBlank()) {
                photoAnalysisService.analyze(userId, albumId, photo.getId());
            }
        }
    }
}
