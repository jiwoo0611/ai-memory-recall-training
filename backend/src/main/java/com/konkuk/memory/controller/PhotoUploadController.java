package com.konkuk.memory.controller;

import com.konkuk.memory.service.PhotoUploadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/albums/{albumId}/photos")
public class PhotoUploadController {

    private final PhotoUploadService photoUploadService;

    public PhotoUploadController(
            PhotoUploadService photoUploadService
    ) {
        this.photoUploadService = photoUploadService;
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
}
