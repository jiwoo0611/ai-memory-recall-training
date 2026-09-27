package com.konkuk.memory.service;

import com.konkuk.memory.dto.TrainingSessionDetailResponse;
import org.springframework.stereotype.Service;

@Service
public class AlbumQuestionGenerationService {

    private final AlbumQuestionContextService contextService;
    private final GeminiQuestionGenerationClient geminiClient;
    private final QuestionGenerationPersistenceService persistenceService;
    private final MyPageService myPageService;

    public AlbumQuestionGenerationService(
            AlbumQuestionContextService contextService,
            GeminiQuestionGenerationClient geminiClient,
            QuestionGenerationPersistenceService persistenceService,
            MyPageService myPageService
    ) {
        this.contextService = contextService;
        this.geminiClient = geminiClient;
        this.persistenceService = persistenceService;
        this.myPageService = myPageService;
    }

    public TrainingSessionDetailResponse generate(Long userId, Long albumId) {
        AlbumQuestionContextService.AlbumQuestionContext context =
                contextService.build(userId, albumId);
        Long sessionId = persistenceService.start(
                userId,
                context.albumId(),
                context.anchorPhotoId()
        );

        try {
            var generated = geminiClient.generate(
                    context.evidenceJson(),
                    context.photoIds()
            );
            persistenceService.complete(
                    userId,
                    sessionId,
                    context.evidenceJson(),
                    generated,
                    geminiClient.generationVersion()
            );
        } catch (RuntimeException ex) {
            persistenceService.fail(userId, sessionId);
            throw ex;
        }

        return myPageService.trainingSession(userId, sessionId);
    }
}