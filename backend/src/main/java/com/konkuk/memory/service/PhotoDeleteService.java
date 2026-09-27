package com.konkuk.memory.service;

import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    // 사진 1장 삭제
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

    // 선택한 사진 여러 장 삭제
    @Transactional
    public void deleteSelected(
            Long userId,
            Long albumId,
            List<Long> photoIds
    ) {
        for (Long photoId : photoIds) {

            Photo photo = photoRepository
                    .findByIdAndAlbum_IdAndUser_Id(
                            photoId,
                            albumId,
                            userId
                    )
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "PHOTO_NOT_FOUND",
                            "사진을 찾을 수 없습니다."
                    ));

            String storageKey = photo.getStorageKey();

            photoRepository.delete(photo);
            photoRepository.flush();

            fileStorageService.delete(storageKey);
        }
    }
}