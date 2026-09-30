package com.konkuk.memory;

import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseIntegrationTest {
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRepository answers;
    @Autowired ResultRepository results;
    @Autowired JdbcTemplate jdbc;

    private User owner(String email) { return users.saveAndFlush(new User(email, "test-fixture-hash", "회원")); }
    private Photo photo(User user) {
        Album album = albums.saveAndFlush(new Album(user, "테스트 앨범"));
        return photos.saveAndFlush(new Photo(user, album, "private/" + user.getEmail(), "photo.jpg", "image/jpeg", 1024));
    }
    private Question question(TrainingSession session) {
        return questions.saveAndFlush(new Question(session, 1, "어떤 기억이 떠오르나요?", Question.Type.EXPERIENCE,
                "[{\"source\":\"AI_INFERENCE\",\"clue\":\"바다\"}]", false, null, "test-v1"));
    }

    @Test void retrainingPreservesIndependentRecordsAndOwnerQueriesExcludeOtherUsers() {
        User owner = owner("owner@example.com");
        User stranger = owner("stranger@example.com");
        Photo photo = photo(owner);
        TrainingSession first = sessions.saveAndFlush(new TrainingSession(photo));
        TrainingSession second = sessions.saveAndFlush(new TrainingSession(photo));
        Question q1 = question(first);
        Question q2 = question(second);
        answers.saveAndFlush(new Answer(q1, Answer.Status.ANSWERED, "친구와 여행했습니다."));
        answers.saveAndFlush(new Answer(q2, Answer.Status.CANNOT_RECALL, null));
        results.saveAndFlush(new Result(first));
        results.saveAndFlush(new Result(second));

        assertThat(sessions.findAllByPhoto_IdAndPhoto_User_Id(photo.getId(), owner.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(answers.findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(first.getId(), owner.getId()))
                .singleElement().extracting(Answer::getContent).isEqualTo("친구와 여행했습니다.");
        assertThat(photos.findByIdAndUser_Id(photo.getId(), stranger.getId())).isEmpty();
        assertThat(sessions.findByIdAndPhoto_User_Id(first.getId(), stranger.getId())).isEmpty();
        assertThat(sessions.findAllByPhoto_User_Id(stranger.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(first.getId(), stranger.getId())).isEmpty();
        assertThat(questions.findByIdAndTrainingSession_IdAndTrainingSession_Photo_User_Id(q1.getId(), second.getId(), owner.getId())).isEmpty();
        assertThat(answers.findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(first.getId(), stranger.getId())).isEmpty();
        assertThat(results.findByTrainingSession_IdAndTrainingSession_Photo_User_Id(first.getId(), stranger.getId())).isEmpty();
        assertThat(results.findByTrainingSession_IdAndTrainingSession_Photo_User_Id(second.getId(), owner.getId())).isPresent();
    }

    @Test void databaseRejectsDuplicateEmailEvenWithoutServiceCheck() {
        owner("duplicate@example.com");
        assertThatThrownBy(() -> owner("duplicate@example.com")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void databaseRejectsDuplicateAnswer() {
        var question = question(sessions.saveAndFlush(new TrainingSession(photo(owner("answer@example.com")))));
        answers.saveAndFlush(new Answer(question, Answer.Status.CANNOT_RECALL, null));
        assertThatThrownBy(() -> answers.saveAndFlush(new Answer(question, Answer.Status.QUESTION_MISMATCH, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void databaseRejectsDuplicateResult() {
        var session = sessions.saveAndFlush(new TrainingSession(photo(owner("result@example.com"))));
        results.saveAndFlush(new Result(session));
        assertThatThrownBy(() -> results.saveAndFlush(new Result(session))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void databaseRejectsDuplicateQuestionOrderWithinSession() {
        var session = sessions.saveAndFlush(new TrainingSession(photo(owner("question@example.com"))));
        question(session);
        assertThatThrownBy(() -> question(session)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void databaseRejectsEmptyTextAnswer() {
        var question = question(sessions.saveAndFlush(new TrainingSession(photo(owner("empty@example.com")))));
        assertThatThrownBy(() -> answers.saveAndFlush(new Answer(question, Answer.Status.ANSWERED, " ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void deletingPhotoCascadesToItsHistory() {
        var photo = photo(owner("delete@example.com"));
        var session = sessions.saveAndFlush(new TrainingSession(photo));
        var q = question(session);
        answers.saveAndFlush(new Answer(q, Answer.Status.CANNOT_RECALL, null));
        results.saveAndFlush(new Result(session));
        jdbc.update("DELETE FROM photos WHERE id = ?", photo.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE id=?", Integer.class, session.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE id=?", Integer.class, q.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answers WHERE question_id=?", Integer.class, q.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE training_session_id=?", Integer.class, session.getId())).isZero();
    }
}
