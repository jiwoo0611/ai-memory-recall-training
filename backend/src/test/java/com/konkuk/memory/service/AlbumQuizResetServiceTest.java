package com.konkuk.memory.service;

import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.entity.Result;
import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.AnswerRepository;
import com.konkuk.memory.repository.QuestionRepository;
import com.konkuk.memory.repository.ResultRepository;
import com.konkuk.memory.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlbumQuizResetServiceTest {

    private final AlbumRepository albums = mock(AlbumRepository.class);
    private final TrainingSessionRepository sessions = mock(TrainingSessionRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final AnswerRepository answers = mock(AnswerRepository.class);
    private final ResultRepository results = mock(ResultRepository.class);
    private final AlbumQuizResetService service = new AlbumQuizResetService(
            albums, sessions, questions, answers, results
    );

    @Test
    void deletesOnlyQuizRecordsFoundForOwnedAlbumInForeignKeyOrder() {
        Long userId = 2L;
        Long albumId = 7L;
        TrainingSession session = mock(TrainingSession.class);
        Question question = mock(Question.class);
        Answer answer = mock(Answer.class);
        Result result = mock(Result.class);

        when(albums.findByIdAndUser_Id(albumId, userId)).thenReturn(Optional.of(mock(Album.class)));
        when(sessions.findAllByPhoto_Album_IdAndPhoto_User_Id(albumId, userId)).thenReturn(List.of(session));
        when(questions.findAllByTrainingSessionIn(List.of(session))).thenReturn(List.of(question));
        when(answers.findAllByQuestionIn(List.of(question))).thenReturn(List.of(answer));
        when(results.findAllByTrainingSessionIn(List.of(session))).thenReturn(List.of(result));

        var response = service.reset(userId, albumId);

        assertThat(response.deletedSessionCount()).isEqualTo(1);
        assertThat(response.deletedQuestionCount()).isEqualTo(1);
        assertThat(response.deletedAnswerCount()).isEqualTo(1);
        assertThat(response.deletedResultCount()).isEqualTo(1);

        InOrder deletionOrder = inOrder(answers, results, questions, sessions);
        deletionOrder.verify(answers).deleteAllInBatch(List.of(answer));
        deletionOrder.verify(results).deleteAllInBatch(List.of(result));
        deletionOrder.verify(questions).deleteAllInBatch(List.of(question));
        deletionOrder.verify(sessions).deleteAllInBatch(List.of(session));
    }

    @Test
    void rejectsAlbumThatDoesNotBelongToCurrentUser() {
        when(albums.findByIdAndUser_Id(7L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset(2L, 7L))
                .isInstanceOf(ApiException.class);

        verify(sessions, never()).findAllByPhoto_Album_IdAndPhoto_User_Id(7L, 2L);
    }
}
