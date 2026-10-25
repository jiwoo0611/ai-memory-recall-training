package com.konkuk.memory.service;

import com.konkuk.memory.dto.TrainingSessionDetailResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class AlbumQuestionGenerationService {

    private final AlbumPhotoAnalysisService albumPhotoAnalysisService;
    private final AlbumQuestionReadinessService readinessService;
    private final AlbumQuestionContextService contextService;
    private final GeminiQuestionGenerationClient geminiClient;
    private final LocationQuestionFactory locationQuestionFactory;
    private final TimelineQuestionFactory timelineQuestionFactory;
    private final QuestionGenerationPersistenceService persistenceService;
    private final MyPageService myPageService;

    public AlbumQuestionGenerationService(
            AlbumPhotoAnalysisService albumPhotoAnalysisService,
            AlbumQuestionReadinessService readinessService,
            AlbumQuestionContextService contextService,
            GeminiQuestionGenerationClient geminiClient,
            LocationQuestionFactory locationQuestionFactory,
            TimelineQuestionFactory timelineQuestionFactory,
            QuestionGenerationPersistenceService persistenceService,
            MyPageService myPageService
    ) {
        this.albumPhotoAnalysisService = albumPhotoAnalysisService;
        this.readinessService = readinessService;
        this.contextService = contextService;
        this.geminiClient = geminiClient;
        this.locationQuestionFactory = locationQuestionFactory;
        this.timelineQuestionFactory = timelineQuestionFactory;
        this.persistenceService = persistenceService;
        this.myPageService = myPageService;
    }

    public TrainingSessionDetailResponse generate(Long userId, Long albumId) {
        albumPhotoAnalysisService.ensureComplete(userId, albumId);
        AlbumQuestionReadinessService.Readiness readiness =
                readinessService.requireReady(userId, albumId);
        AlbumQuestionContextService.AlbumQuestionContext context =
                contextService.build(userId, albumId, readiness.eligiblePhotoIds());
        Long sessionId = persistenceService.start(userId, context.albumId(), context.anchorPhotoId());

        try {
            var generated = new ArrayList<GeminiQuestionGenerationClient.GeneratedQuestion>();
            locationQuestionFactory.create(context.evidenceJson()).ifPresent(generated::add);
            timelineQuestionFactory.create(context.evidenceJson()).ifPresent(generated::add);
            QuestionPlan plan = questionPlan(
                    readiness.eligibleSceneCount(),
                    generated.size()
            );
            generated.addAll(geminiClient.generate(
                    context.evidenceJson(),
                    context.photoIds(),
                    plan.minAiQuestions(),
                    plan.maxAiQuestions()
            ));
            var limited = generated.stream().limit(plan.maxTotalQuestions()).toList();

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

    static QuestionPlan questionPlan(int eligibleSceneCount, int deterministicQuestionCount) {
        int targetMin = eligibleSceneCount >= 8 ? 10 : 8;
        int targetMax = eligibleSceneCount >= 8 ? 13 : 10;
        int minAi = Math.max(6, targetMin - deterministicQuestionCount);
        int maxAi = Math.min(11, targetMax - deterministicQuestionCount);
        return new QuestionPlan(Math.min(minAi, maxAi), maxAi, targetMax);
    }

    record QuestionPlan(
            int minAiQuestions,
            int maxAiQuestions,
            int maxTotalQuestions
    ) {}
}
