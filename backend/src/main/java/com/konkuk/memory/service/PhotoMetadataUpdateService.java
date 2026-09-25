package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoConfirmRequest;
import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoMetadataUpdateService {

    private final PhotoRepository photoRepository;

    public PhotoMetadataUpdateService(
            PhotoRepository photoRepository
    ) {
        this.photoRepository = photoRepository;
    }

    @Transactional
    public PhotoResponse confirm(
            Long userId,
            Long photoId,
            PhotoConfirmRequest request
    ) {

        Photo photo = photoRepository
                .findByIdAndUser_Id(photoId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PHOTO_NOT_FOUND",
                        "사진을 찾을 수 없습니다."
                ));

        String location = normalizeLocation(
                request.location()
        );

        photo.confirmMetadata(
                request.takenOn(),
                location
        );

        return PhotoResponse.from(photo);
    }

    private String normalizeLocation(String location) {

        if (location == null) {
            return null;
        }

        String trimmed = location.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }
}