package com.konkuk.memory.service;

import com.konkuk.memory.dto.TrainingSessionDetailResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class AlbumQuestionGenerationService {

    private static final int MAX_QUESTIONS = 13;

    private final AlbumPhotoAnalysisService albumPhotoAnalysisService;
    private final AlbumQuestionContextService contextService;
    private final GeminiQuestionGenerationClient geminiClient;
    private final LocationQuestionFactory locationQuestionFactory;
    private final TimelineQuestionFactory timelineQuestionFactory;
    private final QuestionGenerationPersistenceService persistenceService;
    private final MyPageService myPageService;

    public AlbumQuestionGenerationService(
            AlbumPhotoAnalysisService albumPhotoAnalysisService,
            AlbumQuestionContextService contextService,
            GeminiQuestionGenerationClient geminiClient,
            LocationQuestionFactory locationQuestionFactory,
            TimelineQuestionFactory timelineQuestionFactory,
            QuestionGenerationPersistenceService persistenceService,
            MyPageService myPageService
    ) {
        this.albumPhotoAnalysisService = albumPhotoAnalysisService;
        this.contextService = contextService;
        this.geminiClient = geminiClient;
        this.locationQuestionFactory = locationQuestionFactory;
        this.timelineQuestionFactory = timelineQuestionFactory;
        this.persistenceService = persistenceService;
        this.myPageService = myPageService;
    }

    public TrainingSessionDetailResponse generate(Long userId, Long albumId) {
        albumPhotoAnalysisService.ensureComplete(userId, albumId);
        AlbumQuestionContextService.AlbumQuestionContext context = contextService.build(userId, albumId);
        Long sessionId = persistenceService.start(userId, context.albumId(), context.anchorPhotoId());

        try {
            var generated = new ArrayList<GeminiQuestionGenerationClient.GeneratedQuestion>();
            locationQuestionFactory.create(context.evidenceJson()).ifPresent(generated::add);
            timelineQuestionFactory.create(context.evidenceJson()).ifPresent(generated::add);
            generated.addAll(geminiClient.generate(context.evidenceJson(), context.photoIds()));
            var limited = generated.stream().limit(MAX_QUESTIONS).toList();

            persistenceService.complete(
                    userId,
                    sessionId,
                    context.evidenceJson(),
                    limited,
                    geminiClient.generationVersion()
            );
        } catch (RuntimeException ex) {
            persistenceService.fail(userId, sessionId);
            throw ex;
        }

        return myPageService.trainingSession(userId, sessionId);
    }
}
