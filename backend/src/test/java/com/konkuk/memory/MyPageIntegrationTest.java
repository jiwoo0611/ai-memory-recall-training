package com.konkuk.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import com.konkuk.memory.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRepository answers;
    @Autowired ResultRepository results;
    @Autowired TokenService tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private Long userId, ownerAlbumId, otherAlbumId, photoId, secondPhotoId, sessionId, emptySessionId, otherSessionId;
    private String token, otherToken, email, storageKey;
    private Path imagePath;

    @BeforeEach
    void fixture() throws IOException {
        // Fixtures commit before requests: lazy loading must work inside the service transaction,
        // not accidentally via an enclosing test transaction. Only the test-profile H2 is used.
        assertThat(jdbc.queryForObject("SELECT H2VERSION()", String.class)).isNotBlank();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            email = "mypage-" + suffix + "@example.com";
            User owner = users.save(new User(email, "fixture-hash-not-for-login", "본인"));
            User other = users.save(new User("other-" + suffix + "@example.com", "fixture-hash-not-for-login", "다른 사용자"));
            userId = owner.getId();
            token = tokens.issue(owner.getId()).accessToken();
            otherToken = tokens.issue(other.getId()).accessToken();
            Album ownerAlbum = albums.save(new Album(owner, "본인 앨범"));
            Album otherAlbum = albums.save(new Album(other, "다른 앨범"));
            ownerAlbumId = ownerAlbum.getId();
            otherAlbumId = otherAlbum.getId();
            storageKey = "test/" + suffix + "/own.jpg";
            Photo photo = photos.save(new Photo(owner, ownerAlbum, storageKey, "own.jpg", "image/jpeg", 100));
            Photo second = photos.save(new Photo(owner, ownerAlbum, "private/" + suffix + "/2", "second.jpg", "image/jpeg", 200));
            Photo foreignPhoto = photos.save(new Photo(other, otherAlbum, "private/" + suffix + "/other", "other.jpg", "image/jpeg", 300));
            photoId = photo.getId();
            secondPhotoId = second.getId();
            TrainingSession full = sessions.save(new TrainingSession(photo));
            TrainingSession empty = sessions.save(new TrainingSession(photo));
            TrainingSession foreign = sessions.save(new TrainingSession(foreignPhoto));
            sessionId = full.getId();
            emptySessionId = empty.getId();
            otherSessionId = foreign.getId();
            Question q2 = question(full, 2);
            Question q1 = question(full, 1);
            Question q3 = question(full, 3);
            question(full, 4);
            answers.save(new Answer(q1, Answer.Status.ANSWERED, "친구와 여행한 기억"));
            answers.save(new Answer(q2, Answer.Status.CANNOT_RECALL, null));
            answers.save(new Answer(q3, Answer.Status.QUESTION_MISMATCH, null));
            results.save(new Result(full));
            Question foreignQuestion = question(foreign, 1);
            answers.save(new Answer(foreignQuestion, Answer.Status.ANSWERED, "다른 사용자 비공개 답변"));
            results.save(new Result(foreign));
        });
        // Fixed fixture timestamps verify the id tie-breaker without sleeps.
        jdbc.update("UPDATE photos SET created_at = '2026-01-01 00:00:00' WHERE user_id = ?", userId);
        jdbc.update("UPDATE training_sessions SET started_at = '2026-01-01 00:00:00' WHERE id IN (?, ?)", sessionId, emptySessionId);
        imagePath = Paths.get("uploads").toAbsolutePath().normalize().resolve(storageKey);
        Files.createDirectories(imagePath.getParent());
        Files.write(imagePath, new byte[]{1, 2, 3, 4});
    }

    @AfterEach
    void deleteFixtureImage() throws IOException {
        if (imagePath == null) return;
        Files.deleteIfExists(imagePath);
        Files.deleteIfExists(imagePath.getParent());
    }

    private Question question(TrainingSession session, int order) {
        return questions.save(new Question(session, order, "질문 " + order, Question.Type.EXPERIENCE,
                "[]", false, null, "fixture-v1"));
    }

    private ResultActions ownGet(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private List<String> paths() {
        return List.of("/api/users/me", "/api/users/me/photos", "/api/users/me/training-sessions",
                "/api/users/me/training-sessions/" + sessionId,
                "/api/albums/" + ownerAlbumId + "/photos/" + photoId,
                "/api/albums/" + ownerAlbumId + "/photos/" + photoId + "/image");
    }

    @Test void allEndpointsRequireJwt() throws Exception {
        for (String path : paths()) mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test void allEndpointsRejectInvalidJwt() throws Exception {
        for (String path : paths()) mvc.perform(get(path).header("Authorization", "Bearer invalid.jwt.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test void photoDetailAndImageAreAlbumScopedOutsideFixtureTransaction() throws Exception {
        ownGet("/api/albums/" + ownerAlbumId + "/photos/" + photoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(photoId))
                .andExpect(jsonPath("albumId").value(ownerAlbumId))
                .andExpect(jsonPath("originalFilename").value("own.jpg"));

        ownGet("/api/albums/" + ownerAlbumId + "/photos/" + photoId + "/image")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));

        ownGet("/api/albums/" + otherAlbumId + "/photos/" + photoId)
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/albums/" + ownerAlbumId + "/photos/" + photoId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test void profileIncludesDatesAndUsesTokenIdentity() throws Exception {
        ownGet("/api/users/me?userId=999999").andExpect(status().isOk())
                .andExpect(jsonPath("id").value(userId)).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("name").value("본인"))
                .andExpect(jsonPath("createdAt").isNotEmpty()).andExpect(jsonPath("updatedAt").isNotEmpty());
    }

    @Test void photosAreOwnedPaginatedAndStablySorted() throws Exception {
        ownGet("/api/users/me/photos?size=1&page=0&userId=999999").andExpect(status().isOk())
                .andExpect(jsonPath("content", hasSize(1))).andExpect(jsonPath("content[0].id").value(secondPhotoId))
                .andExpect(jsonPath("content[0].confirmedLocation").value(nullValue()))
                .andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("totalPages").value(2))
                .andExpect(jsonPath("page").value(0)).andExpect(jsonPath("size").value(1))
                .andExpect(jsonPath("hasNext").value(true));
        ownGet("/api/users/me/photos?size=1&page=1").andExpect(jsonPath("content[0].id").value(photoId))
                .andExpect(jsonPath("hasNext").value(false));
    }

    @Test void sessionsAreOwnedPaginatedAndIncludePhoto() throws Exception {
        ownGet("/api/users/me/training-sessions?size=1&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("content", hasSize(1)))
                .andExpect(jsonPath("content[0].id").value(emptySessionId))
                .andExpect(jsonPath("content[0].photo.id").value(photoId))
                .andExpect(jsonPath("content[0].photo.originalFilename").value("own.jpg"))
                .andExpect(jsonPath("content[0].status").value("CREATED"))
                .andExpect(jsonPath("content[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("content[0].completedAt").value(nullValue()));
        ownGet("/api/users/me/training-sessions?size=1&page=1")
                .andExpect(jsonPath("content[0].id").value(sessionId)).andExpect(jsonPath("hasNext").value(false));
    }

    @Test void detailCombinesOrderedQuestionsAnswersAndResult() throws Exception {
        jdbc.update("UPDATE results SET feedback_status='COMPLETED', summary=?, answered_count=1, cannot_recall_count=1, mismatch_count=1 WHERE training_session_id=?", "친구와 여행을 회상했습니다.", sessionId);
        ownGet("/api/users/me/training-sessions/" + sessionId).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(sessionId)).andExpect(jsonPath("photo.id").value(photoId))
                .andExpect(jsonPath("questions", hasSize(4)))
                .andExpect(jsonPath("questions[*].questionOrder", contains(1, 2, 3, 4)))
                .andExpect(jsonPath("questions[0].answer.content").value("친구와 여행한 기억"))
                .andExpect(jsonPath("questions[0].answer.createdAt").isNotEmpty())
                .andExpect(jsonPath("questions[1].answer.status").value("CANNOT_RECALL"))
                .andExpect(jsonPath("questions[1].answer.content").value(nullValue()))
                .andExpect(jsonPath("questions[2].answer.status").value("QUESTION_MISMATCH"))
                .andExpect(jsonPath("questions[3].answer").value(nullValue()))
                .andExpect(jsonPath("result.feedbackStatus").value("COMPLETED"))
                .andExpect(jsonPath("result.summary").value("친구와 여행을 회상했습니다."))
                .andExpect(jsonPath("result.answeredCount").value(1))
                .andExpect(jsonPath("result.cannotRecallCount").value(1))
                .andExpect(jsonPath("result.mismatchCount").value(1));
    }

    @Test void foreignAndMissingSessionsHaveSameNotFoundResponse() throws Exception {
        for (Long id : List.of(otherSessionId, Long.MAX_VALUE)) {
            ownGet("/api/users/me/training-sessions/" + id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("code").value("TRAINING_SESSION_NOT_FOUND"));
        }
        mvc.perform(get("/api/users/me/training-sessions/" + sessionId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test void samePhotoRetrainingHasIndependentEmptyDetail() throws Exception {
        ownGet("/api/users/me/training-sessions/" + emptySessionId).andExpect(status().isOk())
                .andExpect(jsonPath("photo.id").value(photoId))
                .andExpect(jsonPath("questions", hasSize(0))).andExpect(jsonPath("result").value(nullValue()));
    }

    @Test void unansweredQuestionWithoutResultIsReadable() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> question(sessions.findById(emptySessionId).orElseThrow(), 1));
        ownGet("/api/users/me/training-sessions/" + emptySessionId).andExpect(status().isOk())
                .andExpect(jsonPath("questions", hasSize(1)))
                .andExpect(jsonPath("questions[0].answer").value(nullValue()))
                .andExpect(jsonPath("result").value(nullValue()));
    }

    @Test void pendingAndFailedFeedbackDoNotHideAnswers() throws Exception {
        for (String state : List.of("PENDING", "FAILED")) {
            jdbc.update("UPDATE results SET feedback_status=? WHERE training_session_id=?", state, sessionId);
            ownGet("/api/users/me/training-sessions/" + sessionId).andExpect(status().isOk())
                    .andExpect(jsonPath("result.feedbackStatus").value(state))
                    .andExpect(jsonPath("result.summary").value(nullValue()))
                    .andExpect(jsonPath("questions[0].answer.content").value("친구와 여행한 기억"));
        }
    }

    @Test void emptyAndOutOfRangePagesAreSuccessful() throws Exception {
        User fresh = users.saveAndFlush(new User("empty-" + UUID.randomUUID() + "@example.com", "fixture-hash", "빈 사용자"));
        String freshToken = tokens.issue(fresh.getId()).accessToken();
        for (String path : List.of("/api/users/me/photos", "/api/users/me/training-sessions")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + freshToken)).andExpect(status().isOk())
                    .andExpect(jsonPath("content", hasSize(0))).andExpect(jsonPath("totalElements").value(0));
            ownGet(path + "?page=100").andExpect(status().isOk()).andExpect(jsonPath("content", hasSize(0)));
        }
    }

    @Test void invalidPaginationAndIdsReturnBadRequest() throws Exception {
        for (String path : List.of("/api/users/me/photos", "/api/users/me/training-sessions")) {
            for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=abc", "?size=999999999999", "?page=2147483647&size=20")) {
                ownGet(path + query).andExpect(status().isBadRequest()).andExpect(jsonPath("code").isNotEmpty());
            }
        }
        for (String id : List.of("0", "-1", "abc", "999999999999999999999")) {
            ownGet("/api/users/me/training-sessions/" + id).andExpect(status().isBadRequest());
        }
    }

    @Test void deletedUserTokenIsRejectedEverywhere() throws Exception {
        User deleted = users.saveAndFlush(new User("deleted-" + UUID.randomUUID() + "@example.com", "fixture-hash", "삭제"));
        String deletedToken = tokens.issue(deleted.getId()).accessToken();
        users.deleteById(deleted.getId());
        for (String path : paths()) mvc.perform(get(path).header("Authorization", "Bearer " + deletedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test void responsesNeverExposeInternalFieldsOrOtherUsersContent() throws Exception {
        for (String path : paths()) {
            if (path.endsWith("/image")) continue; // Binary image response is verified by the image endpoint tests.
            String body = ownGet(path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertSafe(mapper.readTree(body));
            assertThat(body).doesNotContain("private/", "fixture-hash", "다른 사용자 비공개 답변", "other.jpg");
        }
    }

    private void assertSafe(JsonNode node) {
        if (node.isObject()) node.properties().forEach(field -> {
            assertThat(field.getKey().toLowerCase(Locale.ROOT)).isNotIn("password", "passwordhash", "password_hash", "storagekey", "storage_key");
            assertSafe(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::assertSafe);
    }

    @Test void readsDoNotChangePersistedData() throws Exception {
        var before = snapshot();
        for (String path : paths()) ownGet(path).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
    }

    private List<List<Map<String, Object>>> snapshot() {
        return List.of("users", "photos", "training_sessions", "questions", "answers", "results").stream()
                .map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
    }
}
