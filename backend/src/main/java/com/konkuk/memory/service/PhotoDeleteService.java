package com.konkuk.memory.service;

import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoDeleteService {

    private final PhotoRepository photoRepository;
    private final FileStorageService fileStorageService;

    public PhotoDeleteService(
            PhotoRepository photoRepository,
            FileStorageService fileStorageService
    ) {
        this.photoRepository = photoRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public void delete(
            Long userId,
            Long albumId,
            Long photoId
    ) {
        Photo photo = photoRepository
                .findByIdAndUser_Id(photoId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PHOTO_NOT_FOUND",
                        "사진을 찾을 수 없습니다."
                ));

        if (!photo.getAlbum().getId().equals(albumId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PHOTO_NOT_FOUND",
                    "사진을 찾을 수 없습니다."
            );
        }

        String storageKey = photo.getStorageKey();

        photoRepository.delete(photo);
        photoRepository.flush();

        fileStorageService.delete(storageKey);
    }
}
