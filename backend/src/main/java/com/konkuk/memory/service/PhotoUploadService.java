package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoMetadata;
import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.entity.User;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PhotoUploadService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;

    private final ImageValidationService imageValidationService;
    private final PhotoMetadataService photoMetadataService;
    private final FileStorageService fileStorageService;

    public PhotoUploadService(
            AlbumRepository albumRepository,
            PhotoRepository photoRepository,
            ImageValidationService imageValidationService,
            PhotoMetadataService photoMetadataService,
            FileStorageService fileStorageService
    ) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.imageValidationService = imageValidationService;
        this.photoMetadataService = photoMetadataService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public List<Long> upload(
            Long userId,
            Long albumId,
            List<MultipartFile> files
    ) {

        if (files == null || files.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILES",
                    "업로드할 사진이 없습니다."
            );
        }

        // Resolve the explicitly chosen album before storing any files. Location is
        // photo metadata only and must never select or create an album.
        Album album = albumRepository.findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ALBUM_NOT_FOUND", "앨범을 찾을 수 없습니다."
                ));
        User user = album.getUser();

        List<String> storedFiles = new ArrayList<>();
        List<Photo> photos = new ArrayList<>();

        try {

            for (MultipartFile file : files) {

                // 1. 이미지 파일 검증
                imageValidationService.validate(file);

                // 2. 사진 메타데이터 추출
                PhotoMetadata metadata =
                        photoMetadataService.extract(file);

                // 5. 파일 확장자 결정
                String extension =
                        getExtension(file.getContentType());

                // 6. 파일 저장
                String storageKey =
                        fileStorageService.store(
                                file,
                                userId,
                                album.getId(),
                                extension
                        );

                storedFiles.add(storageKey);

                // 7. Photo 엔티티 생성
                Photo photo = new Photo(
                        user,
                        album,
                        storageKey,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize()
                );

                // 8. 촬영 시간 / 위치 저장
                photo.updateMetadate(
                        metadata.takenAt(),
                        metadata.location()
                );

                photos.add(photo);
            }

            // 9. 사진 DB 저장
            List<Photo> savedPhotos =
                    photoRepository.saveAll(photos);

            return savedPhotos.stream()
                    .map(Photo::getId)
                    .toList();

        } catch (IOException e) {

            deleteStoredFiles(storedFiles);

            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_STORAGE_FAILED",
                    "사진 저장에 실패했습니다."
            );

        } catch (RuntimeException e) {

            deleteStoredFiles(storedFiles);

            throw e;
        }
    }

    private void deleteStoredFiles(List<String> storedFiles) {

        for (String storageKey : storedFiles) {
            fileStorageService.delete(storageKey);
        }
    }

    private String getExtension(String contentType) {

        if ("image/png".equals(contentType)) {
            return ".png";
        }

        if ("image/webp".equals(contentType)) {
            return ".webp";
        }

        return ".jpg";
    }
}

