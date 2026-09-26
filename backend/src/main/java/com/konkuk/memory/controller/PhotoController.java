package com.konkuk.memory.controller;

import com.konkuk.memory.dto.PhotoConfirmRequest;
import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.service.FileStorageService;
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

    public PhotoController(
            PhotoUploadService photoUploadService,
            PhotoQueryService photoQueryService,
            PhotoMetadataUpdateService photoMetadataUpdateService,
            FileStorageService fileStorageService
    ) {
        this.photoUploadService = photoUploadService;
        this.photoQueryService = photoQueryService;
        this.photoMetadataUpdateService = photoMetadataUpdateService;
        this.fileStorageService = fileStorageService;
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
            @PathVariable Long photoId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return photoQueryService.getPhoto(
                userId,
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

        // 로그인한 사용자가 소유한 사진인지 확인
        Photo photo = photoQueryService.getPhotoEntity(
                userId,
                photoId
        );

        // URL의 albumId와 실제 사진의 albumId가 같은지 확인
        if (!photo.getAlbum().getId().equals(albumId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PHOTO_NOT_FOUND",
                    "사진을 찾을 수 없습니다."
            );
        }

        try {
            byte[] imageBytes =
                    fileStorageService.load(
                            photo.getStorageKey()
                    );

            MediaType mediaType;

            try {
                mediaType =
                        MediaType.parseMediaType(
                                photo.getContentType()
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