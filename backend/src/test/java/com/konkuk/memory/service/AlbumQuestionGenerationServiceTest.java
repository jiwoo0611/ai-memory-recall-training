package com.konkuk.memory.service;

import com.konkuk.memory.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumQuestionGenerationServiceTest {

    @Test
    void alwaysPlansTenQuestionsWithoutDeterministicQuestions() {
        var plan = AlbumQuestionGenerationService.questionPlan(List.of());

        assertThat(plan.aiQuestions()).isEqualTo(10);
        assertThat(plan.candidateAiQuestions()).isEqualTo(13);
        assertThat(plan.totalQuestions()).isEqualTo(10);
        assertThat(plan.aiDifficultyQuota())
                .isEqualTo(new GeminiQuestionGenerationClient.DifficultyQuota(5, 4, 1));
    }

    @Test
    void subtractsLocationAndTimelineFromFixedDifficultyMix() {
        var plan = AlbumQuestionGenerationService.questionPlan(List.of(
                question(Question.Difficulty.EASY),
                question(Question.Difficulty.MEDIUM)
        ));

        assertThat(plan.aiQuestions()).isEqualTo(8);
        assertThat(plan.candidateAiQuestions()).isEqualTo(11);
        assertThat(plan.totalQuestions()).isEqualTo(10);
        assertThat(plan.aiDifficultyQuota())
                .isEqualTo(new GeminiQuestionGenerationClient.DifficultyQuota(4, 3, 1));
    }

    private static GeminiQuestionGenerationClient.GeneratedQuestion question(Question.Difficulty difficulty) {
        return new GeminiQuestionGenerationClient.GeneratedQuestion(
                "질문?", Question.Type.CONTEXT, difficulty,
                List.of("1", "2", "3", "4"), 0,
                List.of(1L), List.of(), true, "근거", 120
        );
    }
}
