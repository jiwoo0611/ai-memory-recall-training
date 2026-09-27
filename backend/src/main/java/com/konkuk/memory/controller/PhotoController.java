package com.konkuk.memory.controller;

import com.konkuk.memory.dto.PhotoConfirmRequest;
import com.konkuk.memory.dto.PhotoDeleteRequest;
import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.service.FileStorageService;
import com.konkuk.memory.service.PhotoDeleteService;
import com.konkuk.memory.service.PhotoMetadataUpdateService;
import com.konkuk.memory.service.PhotoQueryService;
import com.konkuk.memory.service.PhotoUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/albums/{albumId}/photos")
public class PhotoController {

    private final PhotoUploadService photoUploadService;
    private final PhotoQueryService photoQueryService;
    private final PhotoMetadataUpdateService photoMetadataUpdateService;
    private final FileStorageService fileStorageService;
    private final PhotoDeleteService photoDeleteService;

    public PhotoController(
            PhotoUploadService photoUploadService,
            PhotoQueryService photoQueryService,
            PhotoMetadataUpdateService photoMetadataUpdateService,
            FileStorageService fileStorageService,
            PhotoDeleteService photoDeleteService
    ) {
        this.photoUploadService = photoUploadService;
        this.photoQueryService = photoQueryService;
        this.photoMetadataUpdateService = photoMetadataUpdateService;
        this.fileStorageService = fileStorageService;
        this.photoDeleteService = photoDeleteService;
    }

    @PostMapping
    public List<Long> uploadPhotos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return photoUploadService.upload(
                userId,
                albumId,
                files
        );
    }

    @GetMapping
    public List<PhotoResponse> getPhotos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return photoQueryService.getPhotos(
                userId,
                albumId
        );
    }

    @GetMapping("/{photoId}")
    public PhotoResponse getPhoto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId,
            @PathVariable Long photoId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return photoQueryService.getPhoto(
                userId,
                albumId,
                photoId
        );
    }

    @GetMapping("/{photoId}/image")
    public ResponseEntity<byte[]> getPhotoImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId,
            @PathVariable Long photoId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        PhotoQueryService.PhotoFile photo =
                photoQueryService.getPhotoFile(
                        userId,
                        albumId,
                        photoId
                );

        try {
            byte[] imageBytes =
                    fileStorageService.load(
                            photo.storageKey()
                    );

            MediaType mediaType;

            try {
                mediaType =
                        MediaType.parseMediaType(
                                photo.contentType()
                        );
            } catch (Exception e) {
                mediaType =
                        MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity
                    .ok()
                    .header(
                            HttpHeaders.CACHE_CONTROL,
                            "private, max-age=3600"
                    )
                    .contentType(mediaType)
                    .body(imageBytes);

        } catch (IOException e) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PHOTO_FILE_NOT_FOUND",
                    "사진 파일을 찾을 수 없습니다."
            );
        }
    }

    // 사진 1장 삭제
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId,
            @PathVariable Long photoId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        photoDeleteService.delete(
                userId,
                albumId,
                photoId
        );

        return ResponseEntity.noContent().build();
    }

    // 선택한 사진 여러 장 삭제
    @DeleteMapping
    public ResponseEntity<Void> deleteSelectedPhotos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId,
            @RequestBody PhotoDeleteRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        photoDeleteService.deleteSelected(
                userId,
                albumId,
                request.getPhotoIds()
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{photoId}/metadata")
    public PhotoResponse confirmMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long photoId,
            @Valid @RequestBody PhotoConfirmRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return photoMetadataUpdateService.confirm(
                userId,
                photoId,
                request
        );
    }
}