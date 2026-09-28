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

    public AlbumPhotoAnalysisService(
            AlbumRepository albumRepository,
            PhotoRepository photoRepository
    ) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
    }

    public void ensureComplete(Long userId, Long albumId) {
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

        boolean hasMissingAnalysis = photos.stream()
                .anyMatch(photo -> photo.getAiClues() == null || photo.getAiClues().isBlank());
        if (hasMissingAnalysis) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_PHOTO_ANALYSIS_REQUIRED",
                    "사진 AI 분석을 모두 완료한 뒤 질문을 생성해주세요."
            );
        }
    }
}