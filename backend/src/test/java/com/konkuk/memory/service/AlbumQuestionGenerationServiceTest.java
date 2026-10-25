package com.konkuk.memory.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumQuestionGenerationServiceTest {

    @Test
    void plansEightToTenTotalQuestionsForSixOrSevenScenes() {
        var withoutFixedQuestions = AlbumQuestionGenerationService.questionPlan(6, 0);
        var withTwoFixedQuestions = AlbumQuestionGenerationService.questionPlan(7, 2);

        assertThat(withoutFixedQuestions.minAiQuestions()).isEqualTo(8);
        assertThat(withoutFixedQuestions.maxAiQuestions()).isEqualTo(10);
        assertThat(withoutFixedQuestions.maxTotalQuestions()).isEqualTo(10);
        assertThat(withTwoFixedQuestions.minAiQuestions()).isEqualTo(6);
        assertThat(withTwoFixedQuestions.maxAiQuestions()).isEqualTo(8);
    }

    @Test
    void plansTenToThirteenTotalQuestionsForEightOrMoreScenes() {
        var withoutFixedQuestions = AlbumQuestionGenerationService.questionPlan(8, 0);
        var withTwoFixedQuestions = AlbumQuestionGenerationService.questionPlan(10, 2);

        assertThat(withoutFixedQuestions.minAiQuestions()).isEqualTo(10);
        assertThat(withoutFixedQuestions.maxAiQuestions()).isEqualTo(11);
        assertThat(withoutFixedQuestions.maxTotalQuestions()).isEqualTo(13);
        assertThat(withTwoFixedQuestions.minAiQuestions()).isEqualTo(8);
        assertThat(withTwoFixedQuestions.maxAiQuestions()).isEqualTo(11);
    }
}
