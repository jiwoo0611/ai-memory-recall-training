package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoMetadata;
import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.entity.User;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import com.konkuk.memory.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PhotoUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;

    private final ImageValidationService imageValidationService;
    private final PhotoMetadataService photoMetadataService;
    private final FileStorageService fileStorageService;

    public PhotoUploadService(
            AlbumRepository albumRepository,
            UserRepository userRepository,
            PhotoRepository photoRepository,
            ImageValidationService imageValidationService,
            PhotoMetadataService photoMetadataService,
            FileStorageService fileStorageService
    ) {
        this.albumRepository = albumRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.imageValidationService = imageValidationService;
        this.photoMetadataService = photoMetadataService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public List<Long> upload(
            Long userId,
            List<MultipartFile> files
    ) {

        if (files == null || files.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILES",
                    "업로드할 사진이 없습니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."
                ));

        List<String> storedFiles = new ArrayList<>();
        List<Photo> photos = new ArrayList<>();

        try {

            for (MultipartFile file : files) {

                // 1. 이미지 파일 검증
                imageValidationService.validate(file);

                // 2. 사진 메타데이터 추출
                PhotoMetadata metadata =
                        photoMetadataService.extract(file);

                // 3. 위치 정보 가져오기
                String location = metadata.location();

                if (location == null || location.isBlank()) {
                    location = "위치 정보 없음";
                }

                // 4. 위치 이름으로 기존 앨범 찾기
                // 없으면 새로운 앨범 생성
                String albumTitle = location;

                Album album = albumRepository
                        .findByUser_IdAndTitle(userId, albumTitle)
                        .orElseGet(() ->
                                albumRepository.save(
                                        new Album(user, albumTitle)
                                )
                        );

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
