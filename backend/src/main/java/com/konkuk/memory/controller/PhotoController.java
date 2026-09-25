package com.konkuk.memory.controller;

import com.konkuk.memory.dto.PhotoConfirmRequest;
import com.konkuk.memory.service.PhotoMetadataUpdateService;
import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.service.PhotoQueryService;
import com.konkuk.memory.service.PhotoUploadService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/albums/{albumId}/photos")
public class PhotoController {

    private final PhotoUploadService photoUploadService;
    private final PhotoQueryService photoQueryService;
    private final PhotoMetadataUpdateService photoMetadataUpdateService;

    public PhotoController(
            PhotoUploadService photoUploadService,
            PhotoQueryService photoQueryService,
            PhotoMetadataUpdateService photoMetadataUpdateService
    ) {
        this.photoUploadService = photoUploadService;
        this.photoQueryService = photoQueryService;
        this.photoMetadataUpdateService = photoMetadataUpdateService;
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