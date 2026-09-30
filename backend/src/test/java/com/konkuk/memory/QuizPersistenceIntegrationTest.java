package com.konkuk.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.dto.QuizSubmissionRequest;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.*;
import com.konkuk.memory.security.TokenService;
import com.konkuk.memory.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizPersistenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired QuestionRepository questions;
    @MockitoSpyBean AnswerRepository answers;
    @MockitoSpyBean ResultRepository results;
    @MockitoSpyBean QuizSummaryGenerator generator;
    @Autowired QuizSubmissionService quiz;
    @Autowired RecallService recall;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    Long userId, photoId, sessionId, foreignQuestionId;
    List<Long> questionIds;
    String token, otherToken;

    @BeforeEach void fixture() {
        // No enclosing test transaction: each HTTP/service call commits independently.
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            String suffix = UUID.randomUUID().toString();
            var owner = users.save(new User(suffix + "@example.com", "fixture", "본인"));
            var other = users.save(new User("other-" + suffix + "@example.com", "fixture", "다른 사람"));
            userId = owner.getId(); token = tokens.issue(userId).accessToken(); otherToken = tokens.issue(other.getId()).accessToken();
            var album = albums.save(new Album(owner, "퀴즈"));
            var photo = photos.save(new Photo(owner, album, "quiz-test/" + suffix, "사진.jpg", "image/jpeg", 10));
            photoId = photo.getId();
            var session = new TrainingSession(photo); session.markInProgress(); sessions.save(session); sessionId = session.getId();
            questionIds = new ArrayList<>();
            for (int i = 1; i <= 3; i++) questionIds.add(questions.save(question(session, i)).getId());
            var otherSession = new TrainingSession(photo); otherSession.markInProgress(); sessions.save(otherSession);
            foreignQuestionId = questions.save(question(otherSession, 1)).getId();
        });
    }
    Question question(TrainingSession session, int order) {
        return new Question(session, order, "질문 " + order, Question.Type.EXPERIENCE, "{}", false, null, "test",
                Question.Format.MULTIPLE_CHOICE, "[\"보기 A\",\"보기 B\",\"보기 C\",\"보기 D\"]", "{\"correctOptionIndex\":0}");
    }
    QuizSubmissionRequest request() {
        return new QuizSubmissionRequest(List.of(choice(questionIds.get(0), 0), choice(questionIds.get(1), 1), choice(questionIds.get(2), 0)));
    }
    QuizSubmissionRequest.AnswerChoice choice(Long id, Integer index) { return new QuizSubmissionRequest.AnswerChoice(id, index); }
    String path() { return "/api/users/me/training-sessions/" + sessionId; }
    org.springframework.test.web.servlet.ResultActions submit(Object body) throws Exception {
        return mvc.perform(post(path() + "/submit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    int answerCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM answers a JOIN questions q ON q.id=a.question_id WHERE q.training_session_id=?", Integer.class, sessionId); }
    int resultCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE training_session_id=?", Integer.class, sessionId); }
    void assertUnchanged() {
        assertThat(answerCount()).isZero(); assertThat(resultCount()).isZero();
        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.IN_PROGRESS);
        assertThat(session.getCompletedAt()).isNull();
    }

    @Test void savesAnswersScoreCompletionAndFeedbackAndReadsInNewTransaction() throws Exception {
        submit(request()).andExpect(status().isOk()).andExpect(jsonPath("correctCount").value(2))
                .andExpect(jsonPath("incorrectCount").value(1)).andExpect(jsonPath("session.status").value("COMPLETED"))
                .andExpect(jsonPath("session.completedAt").isNotEmpty())
                .andExpect(jsonPath("session.result.feedbackStatus").value("COMPLETED"))
                .andExpect(jsonPath("session.result.generationVersion").value("quiz-rules-v1"));
        assertThat(answerCount()).isEqualTo(3); assertThat(resultCount()).isEqualTo(1);
        mvc.perform(get(path()).header("Authorization", "Bearer " + tokens.issue(userId).accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("result.totalCount").value(3))
                .andExpect(jsonPath("result.answeredCount").value(3)).andExpect(jsonPath("result.correctCount").value(2))
                .andExpect(jsonPath("result.incorrectCount").value(1))
                .andExpect(jsonPath("questions[1].answer.selectedOptionIndex").value(1))
                .andExpect(jsonPath("questions[1].answer.correct").value(false))
                .andExpect(jsonPath("questions[1].answer.correctOption").value("보기 A"))
                .andExpect(jsonPath("questions[1].answer.content").value("보기 B"))
                .andExpect(jsonPath("questions[1].answer.createdAt").isNotEmpty());
    }
    @Test void duplicateSubmissionConflictsWithoutChangingCompletionOrAnswers() throws Exception {
        submit(request()).andExpect(status().isOk());
        var completed = sessions.findById(sessionId).orElseThrow().getCompletedAt();
        submit(request()).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ALREADY_SUBMITTED"));
        assertThat(answerCount()).isEqualTo(3); assertThat(resultCount()).isEqualTo(1);
        assertThat(sessions.findById(sessionId).orElseThrow().getCompletedAt()).isEqualTo(completed);
    }
    @Test void rejectsForeignAndMissingQuestionIds() throws Exception {
        for (Long id : List.of(foreignQuestionId, Long.MAX_VALUE)) {
            var invalid = new ArrayList<>(request().answers()); invalid.set(0, choice(id, 0));
            submit(new QuizSubmissionRequest(invalid)).andExpect(status().isBadRequest()); assertUnchanged();
        }
    }
    @Test void rejectsDuplicateAndIncompleteAnswers() throws Exception {
        var original = request().answers();
        submit(new QuizSubmissionRequest(List.of(original.get(0), original.get(0), original.get(2)))).andExpect(status().isBadRequest());
        submit(new QuizSubmissionRequest(original.subList(0, 2))).andExpect(status().isBadRequest());
        assertUnchanged();
    }
    @Test void rejectsInvalidIndicesAndNullItems() throws Exception {
        for (Integer index : Arrays.asList(-1, 4, null)) {
            var invalid = new ArrayList<>(request().answers()); invalid.set(0, choice(questionIds.get(0), index));
            submit(new QuizSubmissionRequest(invalid)).andExpect(status().isBadRequest());
        }
        submit(Map.of("answers", Arrays.asList((Object) null))).andExpect(status().isBadRequest());
        assertUnchanged();
    }
    @Test void preventsOtherUserFromSubmittingReadingFeedbackOrPhotoHistory() throws Exception {
        mvc.perform(post(path()+"/submit").header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request()))).andExpect(status().isNotFound());
        mvc.perform(get(path()).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path()+"/feedback").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me/training-sessions?photoId="+photoId).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        assertUnchanged();
    }
    @Test void validatesEntireRequestBeforeWriting() throws Exception {
        jdbc.update("UPDATE questions SET options_json='[]' WHERE id=?", questionIds.get(2));
        submit(request()).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @Test void answerSaveFailureRollsBackEarlierAnswersAndCanRetry() {
        AtomicInteger writes = new AtomicInteger();
        doAnswer(call -> {
            Answer answer = call.getArgument(0);
            entityManager.persist(answer);
            entityManager.flush();
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("simulated partial write");
            return answer;
        }).when(answers).save(any(Answer.class));
        assertThatThrownBy(() -> quiz.submit(userId, sessionId, request())).isInstanceOf(IllegalStateException.class);
        assertUnchanged(); reset(answers);
        quiz.submit(userId, sessionId, request()); assertThat(answerCount()).isEqualTo(3);
    }
    @Test void resultSaveFailureRollsBackAnswersAndCompletion() {
        doThrow(new IllegalStateException("simulated result failure")).when(results).save(any(Result.class));
        assertThatThrownBy(() -> quiz.submit(userId, sessionId, request())).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }
    @Test void concurrentSubmissionsCreateOneResultAndOneConflict() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        Callable<Boolean> work = () -> {
            gate.await();
            try { quiz.submit(userId, sessionId, request()); return true; }
            catch (ApiException ex) { assertThat(ex.getMessage()).contains("이미 완료"); return false; }
        };
        try {
            var one = executor.submit(work); var two = executor.submit(work); gate.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(answerCount()).isEqualTo(3); assertThat(resultCount()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void feedbackFailurePreservesScoreAndCanBeRetried() throws Exception {
        doThrow(new IllegalStateException("simulated feedback failure")).when(generator).generate(any(), any());
        submit(request()).andExpect(status().isOk()).andExpect(jsonPath("session.result.feedbackStatus").value("FAILED"));
        mvc.perform(get(path()).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("COMPLETED")).andExpect(jsonPath("result.correctCount").value(2))
                .andExpect(jsonPath("questions[1].answer.content").value("보기 B"));
        reset(generator);
        mvc.perform(post(path()+"/feedback").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("result.feedbackStatus").value("COMPLETED"));
        assertThat(answerCount()).isEqualTo(3); assertThat(resultCount()).isEqualTo(1);
    }
    @Test void pendingFeedbackDoesNotBlockReadingCommittedAnswers() throws Exception {
        quiz.submit(userId, sessionId, request());
        mvc.perform(get(path()).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("result.feedbackStatus").value("PENDING"))
                .andExpect(jsonPath("result.correctCount").value(2)).andExpect(jsonPath("questions[0].answer.correct").value(true));
    }
    @Test void emptyHistoryReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/users/me/training-sessions").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty()).andExpect(jsonPath("totalElements").value(0));
    }
    @Test void retrainingPreservesSnapshotsAndListsDistinctSessionsNewestFirst() throws Exception {
        quiz.submit(userId, sessionId, request());
        String key = UUID.randomUUID().toString();
        Long next = recall.retrain(userId, sessionId, key);
        assertThat(recall.retrain(userId, sessionId, key)).isEqualTo(next);
        assertThat(next).isNotEqualTo(sessionId);
        assertThat(answerCount()).isEqualTo(3); assertThat(resultCount()).isEqualTo(1);
        mvc.perform(get("/api/users/me/training-sessions?photoId="+photoId+"&size=1&page=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(next))
                .andExpect(jsonPath("hasNext").value(true)).andExpect(jsonPath("totalElements").value(3));
        mvc.perform(get("/api/users/me/training-sessions?photoId="+photoId+"&size=1&page=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(sessionId))
                .andExpect(jsonPath("content[0].result.correctCount").value(2));
        mvc.perform(get("/api/users/me/training-sessions/"+next).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("IN_PROGRESS"))
                .andExpect(jsonPath("questions[0].options[0]").value("보기 A"))
                .andExpect(jsonPath("questions[0].answer").isEmpty());
    }
    @Test void rejectsSessionsNotInProgress() throws Exception {
        for (String state : List.of("CREATED", "GENERATING_QUESTIONS", "QUESTION_GENERATION_FAILED")) {
            jdbc.update("UPDATE training_sessions SET status=? WHERE id=?", state, sessionId);
            submit(request()).andExpect(status().isConflict());
        }
        assertThat(answerCount()).isZero(); assertThat(resultCount()).isZero();
    }
}
