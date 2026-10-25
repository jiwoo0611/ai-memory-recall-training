package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiQuestionGenerationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiQuestionGenerationClient client =
            new GeminiQuestionGenerationClient(
                    "test-key",
                    "gemini-3.5-flash-lite",
                    objectMapper
            );

    @Test
    void normalizesEightQuestionsWithShownAndHiddenPhotoModes() throws Exception {
        var questions = client.normalize(validOutput(8), Set.of(11L, 12L));

        assertThat(questions).hasSize(8);
        assertThat(questions.get(0).content()).isEqualTo("장면 1에서 함께 준비한 음식은 무엇인가요?");
        assertThat(questions.get(0).options()).containsExactly("정답 1", "오답 1-A", "오답 1-B", "오답 1-C");
        assertThat(questions.get(0).correctOptionIndex()).isZero();
        assertThat(questions.get(0).evidencePhotoIds()).containsExactly(11L);
        assertThat(questions.get(0).displayPhotoIds()).isEmpty();
        assertThat(questions.get(3).displayPhotoIds()).containsExactly(11L);
        assertThat(questions.get(0).difficulty()).isEqualTo(Question.Difficulty.EASY);
        assertThat(questions.get(6).difficulty()).isEqualTo(Question.Difficulty.HARD);
        assertThat(questions.stream().filter(question -> question.difficulty() == Question.Difficulty.HARD))
                .hasSize(1);
        assertThat(questions.get(2).displayPhotoIds()).isEmpty();
        assertThat(questions.get(0).verifiable()).isTrue();
        assertThat(questions.get(1).type()).isEqualTo(Question.Type.CONTEXT);
        assertThat(client.generationVersion())
                .isEqualTo("gemini-3.5-flash-lite:album-recall-mcq-v8");
    }

    @Test
    void supportsSixQuestionPlanForAlbumsWithFewerRepresentativeScenes() throws Exception {
        assertThat(client.normalize(validOutput(6), Set.of(11L, 12L), 6, 8))
                .hasSize(6);
    }

    @Test
    void removesDuplicateAnswersFromExtraCandidatesBeforeSelection() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(9));
        ObjectNode duplicate = (ObjectNode) root.path("questions").path(8);
        duplicate.set("options", objectMapper.valueToTree(new String[]{
                "정답 1", "새 오답 A", "새 오답 B", "새 오답 C"
        }));

        assertThat(client.normalize(
                root.toString(),
                Set.of(11L, 12L),
                8,
                8,
                9
        )).hasSize(8);
    }

    @Test
    void limitsQuestionsThatDependOnTheSamePhotoWhenEnoughPhotosExist() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(10));
        for (int index = 6; index < 10; index++) {
            ((ObjectNode) root.path("questions").path(index)).set(
                    "evidencePhotoIds",
                    objectMapper.valueToTree(new Long[]{12L + (index - 6)})
            );
        }

        var selected = client.normalize(
                root.toString(),
                Set.of(11L, 12L, 13L, 14L, 15L, 16L),
                6,
                8,
                10
        );

        assertThat(selected).hasSizeBetween(6, 8);
        assertThat(selected.stream().filter(question -> question.evidencePhotoIds().contains(11L)))
                .hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void rejectsFewerThanEightQuestions() throws Exception {
        assertThatThrownBy(() -> client.normalize(validOutput(7), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("응답을 질문으로 처리하지 못했습니다");
    }

    @Test
    void rejectsQuestionWithoutFourUniqueOptions() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ObjectNode first = (ObjectNode) root.path("questions").path(0);
        first.set("options", objectMapper.valueToTree(
                new String[]{"정답 1", "정답 1", "오답 1-B", "오답 1-C"}
        ));

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("응답을 질문으로 처리하지 못했습니다");
    }

    @Test
    void rejectsPeopleCountQuestions() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "사진 속 사람은 몇 명이었나요?");

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMetadataOnlyQuestion() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "이 사진은 몇 시에 촬영되었나요?");

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsQuestionsAboutOverlyFineVisualDetails() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "불멍할 때 화로에 있던 무늬는 무엇인가요?");

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsQuestionThatContainsItsCorrectAnswer() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "캠핑에서 정답 1을 먹었던 때를 기억하나요?");

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsOverExplainedQuestion() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "캠핑을 하던 날 모닥불 옆에 놓인 테이블에서 여러 음식과 함께 준비해서 마셨던 음료의 종류 가운데 당시 사진 속 장면과 가장 잘 맞는 것은 무엇인가요?");

        assertThatThrownBy(() -> client.normalize(root.toString(), Set.of(11L, 12L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void allowsProminentColorQuestions() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ((ObjectNode) root.path("questions").path(0))
                .put("content", "불멍 사진에서 불꽃은 어떤 색이었나요?");

        assertThat(client.normalize(root.toString(), Set.of(11L, 12L)))
                .hasSize(8);
    }

    @Test
    void safelyHandlesEmptyGeminiErrorBody() {
        assertThat(client.extractUpstreamMessage(null)).isEmpty();
        assertThat(client.extractUpstreamMessage("")).isEmpty();
        assertThat(client.extractUpstreamMessage("not-json")).isEmpty();
    }

    @Test
    void removesDisplayPhotoOutsideQuestionEvidence() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ObjectNode first = (ObjectNode) root.path("questions").path(3);
        first.set("displayPhotoIds", objectMapper.valueToTree(new Long[]{12L}));

        var questions = client.normalize(root.toString(), Set.of(11L, 12L));

        assertThat(questions).hasSize(8);
        assertThat(questions.get(3).displayPhotoIds()).isEmpty();
    }

    @Test
    void limitsQuestionEvidenceToFourPhotos() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validOutput(8));
        ObjectNode first = (ObjectNode) root.path("questions").path(0);
        first.set("evidencePhotoIds", objectMapper.valueToTree(
                new Long[]{11L, 12L, 13L, 14L, 15L}
        ));

        var questions = client.normalize(
                root.toString(),
                Set.of(11L, 12L, 13L, 14L, 15L)
        );

        assertThat(questions).hasSize(8);
        assertThat(questions.get(0).evidencePhotoIds())
                .containsExactly(11L, 12L, 13L, 14L);
    }

    private String validOutput(int count) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode questions = root.putArray("questions");
        for (int index = 0; index < count; index++) {
            int number = index + 1;
            ObjectNode question = questions.addObject();
            question.put("content", "장면 " + number + "에서 함께 준비한 음식은 무엇인가요?");
            question.put("type", index % 2 == 0 ? "PHOTO_CLUE" : "CONTEXT");
            question.put("difficulty", index < 3 ? "EASY" : index < 6 ? "MEDIUM" : "HARD");
            question.set("options", objectMapper.valueToTree(new String[]{
                    "정답 " + number,
                    "오답 " + number + "-A",
                    "오답 " + number + "-B",
                    "오답 " + number + "-C"
            }));
            question.put("correctOptionIndex", 0);
            question.set("evidencePhotoIds", objectMapper.valueToTree(
                    index >= 6 ? new Long[]{11L, 12L} : new Long[]{11L}
            ));
            question.set("displayPhotoIds", objectMapper.valueToTree(
                    index == 3 || index == 4 ? new Long[]{11L} : new Long[]{}
            ));
            question.put("referenceInformation", "사진 11의 AI 단서 " + number);
        }
        return objectMapper.writeValueAsString(root);
    }
}
