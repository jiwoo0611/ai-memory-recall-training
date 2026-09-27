package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoAnalysisPersistenceService {

    private final PhotoRepository photoRepository;

    public PhotoAnalysisPersistenceService(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    @Transactional
    public PhotoResponse save(
            Long userId,
            Long albumId,
            Long photoId,
            String aiClues
    ) {
        Photo photo = photoRepository
                .findByIdAndAlbum_IdAndUser_Id(photoId, albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PHOTO_NOT_FOUND",
                        "사진을 찾을 수 없습니다."
                ));

        photo.updateAiClues(aiClues);
        return PhotoResponse.from(photo);
    }
}