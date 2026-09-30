package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.dto.QuizSubmissionRequest;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.QuestionRepository;
import com.konkuk.memory.repository.TrainingSessionRepository;
import com.konkuk.memory.repository.AnswerRepository;
import com.konkuk.memory.repository.ResultRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizSubmissionServiceTest {

    private final TrainingSessionRepository sessions = mock(TrainingSessionRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuizSubmissionService service =
            new QuizSubmissionService(sessions, questions, new ObjectMapper(), mock(AnswerRepository.class), mock(ResultRepository.class));

    @Test
    void scoresAnswersAndReturnsWrongChoiceWithCorrectAnswer() {
        Question first = question(101L, "먹었던 음식은 무엇인가요?", "EASY", 0);
        Question second = question(102L, "활동 순서로 알맞은 것은 무엇인가요?", "HARD", 2);
        when(sessions.lockOwned(9L, 2L)).thenReturn(Optional.of(activeSession()));
        when(questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(9L, 2L))
                .thenReturn(List.of(first, second));

        var response = service.submit(2L, 9L, new QuizSubmissionRequest(List.of(
                new QuizSubmissionRequest.AnswerChoice(101L, 0),
                new QuizSubmissionRequest.AnswerChoice(102L, 1)
        )));

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.incorrectCount()).isEqualTo(1);
        assertThat(response.results().get(0).correct()).isTrue();
        assertThat(response.results().get(1).correct()).isFalse();
        assertThat(response.results().get(1).selectedOption()).isEqualTo("보기 B");
        assertThat(response.results().get(1).correctOption()).isEqualTo("보기 C");
        assertThat(response.results().get(1).difficulty()).isEqualTo(Question.Difficulty.HARD);
    }

    @Test
    void rejectsIncompleteSubmission() {
        Question first = question(101L, "첫 질문은 무엇인가요?", "EASY", 0);
        Question second = question(102L, "둘째 질문은 무엇인가요?", "MEDIUM", 1);
        when(sessions.lockOwned(9L, 2L)).thenReturn(Optional.of(activeSession()));
        when(questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(9L, 2L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.submit(2L, 9L, new QuizSubmissionRequest(List.of(
                new QuizSubmissionRequest.AnswerChoice(101L, 0)
        ))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("모든 질문에 답한 후");
    }

    private Question question(Long id, String content, String difficulty, int correctIndex) {
        Question question = mock(Question.class);
        when(question.getFormat()).thenReturn(Question.Format.MULTIPLE_CHOICE);
        when(question.getId()).thenReturn(id);
        when(question.getContent()).thenReturn(content);
        when(question.getEvidenceSnapshot())
                .thenReturn("{\"difficulty\":\"" + difficulty + "\"}");
        when(question.getOptionsJson())
                .thenReturn("[\"보기 A\",\"보기 B\",\"보기 C\",\"보기 D\"]");
        when(question.getAnswerKey())
                .thenReturn("{\"correctOptionIndex\":" + correctIndex + "}");
        return question;
    }
    private TrainingSession activeSession() {
        TrainingSession session = new TrainingSession(null);
        session.markInProgress();
        return session;
    }
}
