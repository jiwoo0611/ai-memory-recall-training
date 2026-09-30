package com.konkuk.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.dto.RecallSubmissionRequest;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import com.konkuk.memory.security.TokenService;
import com.konkuk.memory.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecallIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRepository answers;
    @MockitoSpyBean ResultRepository results;
    @MockitoSpyBean RecallSummaryGenerator generator;
    @Autowired RecallService recall;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    Long userId, albumId, photoId, sessionId;
    List<Long> questionIds;
    String token, otherToken;

    @BeforeEach void fixture() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            String suffix = UUID.randomUUID().toString();
            var owner = users.save(new User(suffix + "@example.com", "fixture", "본인"));
            var other = users.save(new User("other-" + suffix + "@example.com", "fixture", "다른 사람"));
            userId = owner.getId(); token = tokens.issue(userId).accessToken(); otherToken = tokens.issue(other.getId()).accessToken();
            var album = albums.save(new Album(owner, "산책")); albumId = album.getId();
            var photo = photos.save(new Photo(owner, album, "recall-test/" + suffix, "산책.jpg", "image/jpeg", 10)); photoId = photo.getId();
            var session = new TrainingSession(photo); session.markInProgress(); sessions.save(session); sessionId = session.getId();
            questionIds = new ArrayList<>();
            for (int i = 1; i <= 3; i++) questionIds.add(questions.save(new Question(session, i, "기억 " + i,
                    Question.Type.EXPERIENCE, "{}", false, null, "test")).getId());
        });
    }
    RecallSubmissionRequest request() {
        return new RecallSubmissionRequest(List.of(
                new RecallSubmissionRequest.Item(questionIds.get(0), Answer.Status.ANSWERED, "가족과 산책했어요."),
                new RecallSubmissionRequest.Item(questionIds.get(1), Answer.Status.CANNOT_RECALL, null),
                new RecallSubmissionRequest.Item(questionIds.get(2), Answer.Status.QUESTION_MISMATCH, null)));
    }
    String path() { return "/api/users/me/training-sessions/" + sessionId; }
    org.springframework.test.web.servlet.ResultActions submit(Object body) throws Exception {
        return mvc.perform(post(path() + "/answers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    int answerCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM answers a JOIN questions q ON q.id=a.question_id WHERE q.training_session_id=?", Integer.class, sessionId); }

    @Test void savesAllStatesAndReplaysWithoutDuplicates() throws Exception {
        submit(request()).andExpect(status().isOk()).andExpect(jsonPath("status").value("COMPLETED"))
                .andExpect(jsonPath("completedAt").isNotEmpty()).andExpect(jsonPath("result.feedbackStatus").value("PENDING"))
                .andExpect(jsonPath("result.answeredCount").value(1)).andExpect(jsonPath("result.cannotRecallCount").value(1))
                .andExpect(jsonPath("result.mismatchCount").value(1));
        submit(request()).andExpect(status().isOk());
        assertThat(answerCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE training_session_id=?", Integer.class, sessionId)).isEqualTo(1);
        mvc.perform(get(path()).header("Authorization", "Bearer " + tokens.issue(userId).accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("questions[0].answer.content").value("가족과 산책했어요."));
    }
    @Test void rejectsBlankLongMissingDuplicateAndForeignQuestionsWithoutWriting() throws Exception {
        var original = request().answers();
        for (String text : List.of(" \n\t", "가".repeat(2001))) {
            var invalid = new ArrayList<>(original); invalid.set(0, new RecallSubmissionRequest.Item(questionIds.get(0), Answer.Status.ANSWERED, text));
            submit(new RecallSubmissionRequest(invalid)).andExpect(status().isBadRequest());
        }
        submit(new RecallSubmissionRequest(original.subList(0,2))).andExpect(status().isBadRequest());
        submit(new RecallSubmissionRequest(List.of(original.get(0), original.get(0), original.get(2)))).andExpect(status().isBadRequest());
        submit(new RecallSubmissionRequest(List.of(original.get(0), original.get(1), new RecallSubmissionRequest.Item(Long.MAX_VALUE, Answer.Status.CANNOT_RECALL, null))))
                .andExpect(status().isBadRequest());
        submit(Map.of("answers", Arrays.asList((Object) null))).andExpect(status().isBadRequest());
        assertThat(answerCount()).isZero();
    }
    @Test void changedReplayCannotOverwriteSavedAnswers() throws Exception {
        submit(request()).andExpect(status().isOk());
        var changed = new ArrayList<>(request().answers());
        changed.set(0, new RecallSubmissionRequest.Item(questionIds.get(0), Answer.Status.ANSWERED, "변경"));
        submit(new RecallSubmissionRequest(changed)).andExpect(status().isConflict());
        assertThat(answerCount()).isEqualTo(3);
    }
    @Test void differentUserCannotReadSubmitGenerateOrRetrain() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path()+"/answers").header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request()))).andExpect(status().isNotFound());
        mvc.perform(post(path()+"/feedback").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path()+"/retrain").header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("requestId", UUID.randomUUID())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me/training-sessions?photoId="+photoId).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
    }
    @Test void concurrentReplayCreatesOnlyOneSetOfAnswers() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> { gate.await(); recall.submit(userId, sessionId, request()); return true; });
            var two = executor.submit(() -> { gate.await(); recall.submit(userId, sessionId, request()); return true; });
            gate.countDown(); assertThat(one.get(15, TimeUnit.SECONDS)).isTrue(); assertThat(two.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(answerCount()).isEqualTo(3);
        } finally { executor.shutdownNow(); }
    }
    @Test void resultSaveFailureRollsBackAnswersAndCompletionAndCanRetry() {
        doThrow(new IllegalStateException("simulated storage failure")).when(results).save(any(Result.class));
        assertThatThrownBy(() -> recall.submit(userId, sessionId, request())).isInstanceOf(IllegalStateException.class);
        assertThat(answerCount()).isZero();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(TrainingSession.Status.IN_PROGRESS);
        reset(results);
        recall.submit(userId, sessionId, request()); assertThat(answerCount()).isEqualTo(3);
    }
    @Test void feedbackFailureKeepsAnswersAndCanRetry() throws Exception {
        submit(request()).andExpect(status().isOk());
        doThrow(new IllegalStateException("simulated generator failure")).when(generator).generate(any());
        mvc.perform(post(path()+"/feedback").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("result.feedbackStatus").value("FAILED")).andExpect(jsonPath("questions[0].answer.content").value("가족과 산책했어요."));
        doCallRealMethod().when(generator).generate(any());
        mvc.perform(post(path()+"/feedback").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("result.feedbackStatus").value("COMPLETED"));
        assertThat(answerCount()).isEqualTo(3);
    }
    @Test void retrainIsIdempotentAndPreservesHistory() throws Exception {
        submit(request()).andExpect(status().isOk());
        String body = mapper.writeValueAsString(Map.of("requestId", UUID.randomUUID()));
        String first = mvc.perform(post(path()+"/retrain").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(post(path()+"/retrain").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(replay); assertThat(answerCount()).isEqualTo(3);
        mvc.perform(get("/api/users/me/training-sessions?photoId="+photoId+"&size=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("hasNext").value(true));
    }
    @Test void deletionRemovesHistoryAndQueuesOnlyCommittedFiles() throws Exception {
        submit(request()).andExpect(status().isOk());
        recall.retrain(userId, sessionId, UUID.randomUUID().toString());
        mvc.perform(delete("/api/albums/"+albumId+"/photos/"+photoId).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        assertThat(answerCount()).isEqualTo(3);
        mvc.perform(delete("/api/albums/"+albumId+"/photos/"+photoId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(sessions.findById(sessionId)).isEmpty(); assertThat(answerCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE photo_id=?", Integer.class, photoId)).isZero();
    }
    @Test void bulkDeleteFailureRollsBackAllRecordsAndFileJobs() throws Exception {
        submit(request()).andExpect(status().isOk());
        mvc.perform(delete("/api/albums/"+albumId+"/photos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("photoIds", List.of(photoId, Long.MAX_VALUE)))))
                .andExpect(status().isNotFound());
        assertThat(photos.existsById(photoId)).isTrue(); assertThat(answerCount()).isEqualTo(3);
        String key = jdbc.queryForObject("SELECT storage_key FROM photos WHERE id=?", String.class, photoId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_deletions WHERE storage_key=?", Integer.class, key)).isZero();
    }
    @Test void longRetrainingHistoryCanBeDeletedWithoutRecursiveSessionCascade() throws Exception {
        Long previous = sessionId;
        for (int i = 0; i < 20; i++) previous = recall.retrain(userId, previous, UUID.randomUUID().toString());
        mvc.perform(delete("/api/albums/"+albumId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE photo_id=?", Integer.class, photoId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_retries WHERE source_session_id=?", Integer.class, sessionId)).isZero();
    }
}
