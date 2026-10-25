package com.konkuk.memory.service;

import com.konkuk.memory.dto.TrainingSessionDetailResponse;
import com.konkuk.memory.entity.Question;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
            QuestionPlan plan = questionPlan(generated);
            generated.addAll(geminiClient.generate(
                    context.evidenceJson(),
                    context.photoIds(),
                    plan.aiQuestions(),
                    plan.aiQuestions(),
                    plan.candidateAiQuestions(),
                    plan.aiDifficultyQuota()
            ));
            if (generated.size() != plan.totalQuestions()) {
                throw new IllegalStateException("Exactly 10 quiz questions must be generated.");
            }

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

    static QuestionPlan questionPlan(List<GeminiQuestionGenerationClient.GeneratedQuestion> deterministicQuestions) {
        int deterministicEasy = countDifficulty(deterministicQuestions, Question.Difficulty.EASY);
        int deterministicMedium = countDifficulty(deterministicQuestions, Question.Difficulty.MEDIUM);
        int deterministicHard = countDifficulty(deterministicQuestions, Question.Difficulty.HARD);
        int deterministicCount = deterministicQuestions.size();
        if (deterministicCount > 2
                || deterministicEasy > 5
                || deterministicMedium > 4
                || deterministicHard > 1) {
            throw new IllegalArgumentException("Deterministic question mix exceeds the fixed quiz plan.");
        }

        int aiQuestions = 10 - deterministicCount;
        int candidateAi = Math.min(13, aiQuestions + 3);
        return new QuestionPlan(
                aiQuestions,
                candidateAi,
                new GeminiQuestionGenerationClient.DifficultyQuota(
                        5 - deterministicEasy,
                        4 - deterministicMedium,
                        1 - deterministicHard
                ),
                10
        );
    }

    private static int countDifficulty(
            List<GeminiQuestionGenerationClient.GeneratedQuestion> questions,
            Question.Difficulty difficulty
    ) {
        return (int) questions.stream()
                .filter(question -> question.difficulty() == difficulty)
                .count();
    }

    record QuestionPlan(
            int aiQuestions,
            int candidateAiQuestions,
            GeminiQuestionGenerationClient.DifficultyQuota aiDifficultyQuota,
            int totalQuestions
    ) {}
}
