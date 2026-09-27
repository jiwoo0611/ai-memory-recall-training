package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PhotoAnalysisService {

    private final PhotoQueryService photoQueryService;
    private final FileStorageService fileStorageService;
    private final GeminiPhotoAnalysisClient geminiPhotoAnalysisClient;
    private final PhotoAnalysisPersistenceService persistenceService;

    public PhotoAnalysisService(
            PhotoQueryService photoQueryService,
            FileStorageService fileStorageService,
            GeminiPhotoAnalysisClient geminiPhotoAnalysisClient,
            PhotoAnalysisPersistenceService persistenceService
    ) {
        this.photoQueryService = photoQueryService;
        this.fileStorageService = fileStorageService;
        this.geminiPhotoAnalysisClient = geminiPhotoAnalysisClient;
        this.persistenceService = persistenceService;
    }

    public PhotoResponse analyze(
            Long userId,
            Long albumId,
            Long photoId
    ) {
        PhotoQueryService.PhotoFile photo = photoQueryService
                .getPhotoFile(userId, albumId, photoId);

        byte[] imageBytes;
        try {
            imageBytes = fileStorageService.load(photo.storageKey());
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PHOTO_FILE_NOT_FOUND",
                    "원본 사진 파일을 찾을 수 없어 분석할 수 없습니다."
            );
        }

        String aiClues = geminiPhotoAnalysisClient.analyze(
                imageBytes,
                photo.contentType()
        );

        return persistenceService.save(
                userId,
                albumId,
                photoId,
                aiClues
        );
    }
}