package com.konkuk.memory.service;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import com.konkuk.memory.security.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.konkuk.memory.config.SecurityConfig;
import com.konkuk.memory.controller.DelayedRecallController;
import com.konkuk.memory.exception.GlobalExceptionHandler;
import com.konkuk.memory.security.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=DelayedRecallIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.test.context.TestExecutionListeners(listeners={
    org.springframework.test.context.web.ServletTestExecutionListener.class,
    org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    org.springframework.test.context.transaction.TransactionalTestExecutionListener.class
},mergeMode= TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class DelayedRecallIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.konkuk.memory.entity")
    @EnableJpaRepositories("com.konkuk.memory.repository")
    @Import({DelayedRecallService.class, DelayedRecallGradingService.class,
            DelayedRecallController.class, SecurityConfig.class,
            SecurityErrorHandler.class, RevokedTokenStore.class,
            RevokedTokenRepository.class, TokenService.class,
            GlobalExceptionHandler.class})
    static class TestApp {}
    @MockitoBean GeminiRecallGradingClient gradingClient;
    @Autowired DelayedRecallGradingService gradingService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired DelayedRecallService service;
    @Autowired TokenService tokens;
    @Autowired jakarta.persistence.EntityManager entityManager;
    long userId,otherId,sessionId,photoId,secondPhotoId,id;
    String token,otherToken;
    final Instant now=Instant.parse("2026-11-24T12:00:00Z");
    DelayedRecallService deterministic;
    List<DelayedRecallService.PhotoFact> references;
    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString();
        var owner=users.saveAndFlush(new User(suffix+"@example.com","fixture","본인"));
        var other=users.saveAndFlush(new User("other"+suffix+"@example.com","fixture","다른 사용자"));
        userId=owner.getId();otherId=other.getId();token=tokens.issue(userId).accessToken();otherToken=tokens.issue(otherId).accessToken();
        var album=albums.saveAndFlush(new Album(owner,"노출하면 안 되는 앨범 제목"));
        var photo=photos.saveAndFlush(new Photo(owner,album,suffix,"노출하면 안 되는 사진.jpg","image/jpeg",1));photoId=photo.getId();
        secondPhotoId=photos.saveAndFlush(new Photo(owner,album,suffix+"-second","second.jpg","image/jpeg",1)).getId();
        var session=new TrainingSession(photo);session.markInProgress();sessionId=sessions.saveAndFlush(session).getId();
        deterministic=new DelayedRecallService(db,sessions,photos,Clock.fixed(now,ZoneOffset.UTC));
        references=List.of(new DelayedRecallService.PhotoFact(photoId,"부산에 갔다"),new DelayedRecallService.PhotoFact(secondPhotoId,"친구와 케이크를 먹었다"));
        id=deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(references,true)).id();
    }
    void complete(Instant at){entityManager.flush();db.update("UPDATE training_sessions SET started_at=?,completed_at=?,status='COMPLETED' WHERE id=?",Timestamp.from(at.minusSeconds(100)),Timestamp.from(at),sessionId);entityManager.clear();}
    String path(){return "/api/users/me/delayed-recalls/"+id;}
    String body(Object x)throws Exception{return json.writeValueAsString(x);}

    @Test void persistsOneFactPerPhotoAndRejectsOtherAlbumsOrOwners() throws Exception {
        assertThat(db.queryForList("SELECT photo_id FROM delayed_recall_facts WHERE delayed_recall_id=? ORDER BY fact_order",Long.class,id))
            .containsExactly(photoId,secondPhotoId);
        var album=albums.saveAndFlush(new Album(users.findById(userId).orElseThrow(),"다른 여행"));
        var differentPhoto=photos.saveAndFlush(new Photo(users.findById(userId).orElseThrow(),album,"different","different.jpg","image/jpeg",1));
        var request=new DelayedRecallService.Setup(List.of(new DelayedRecallService.PhotoFact(differentPhoto.getId(),"다른 앨범 정보")),true);
        mvc.perform(post("/api/users/me/training-sessions/"+sessionId+"/delayed-recall").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body(request))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/me/training-sessions/"+sessionId+"/delayed-recall").header("Authorization","Bearer "+otherToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(new DelayedRecallService.Setup(references,true)))).andExpect(status().isNotFound());
    }
    @Test void albumListOnlyReturnsOwnedEvaluationsFromThatAlbum() throws Exception {
        long albumId=photos.findById(photoId).orElseThrow().getAlbum().getId();
        var otherAlbum=albums.saveAndFlush(new Album(users.findById(userId).orElseThrow(),"같은 지역 다른 여행"));
        assertThat(service.listForAlbum(userId,albumId)).extracting(DelayedRecallService.View::id).containsExactly(id);
        assertThat(service.listForAlbum(userId,otherAlbum.getId())).isEmpty();
        assertThat(service.listForAlbum(otherId,albumId)).isEmpty();
    }
    @Test void exactSevenDayBoundaryAndNoReferenceLeak() throws Exception {
        complete(now.minus(Duration.ofDays(7)).plusNanos(1000));
        assertThat(deterministic.get(userId,id).status()).isEqualTo("NOT_DUE");
        assertThatThrownBy(()->deterministic.start(userId,id)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        complete(now.minus(Duration.ofDays(7)));
        assertThat(deterministic.start(userId,id).status()).isEqualTo("IN_PROGRESS");
        assertThat(deterministic.get(userId,id).facts()).isEmpty();
        String result=mvc.perform(get(path()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(result).doesNotContain("부산","케이크","사진.jpg");
        assertThat(json.readTree(result).path("displayName").asText()).isEqualTo("노출하면 안 되는 앨범 제목 1");
    }
    @Test void persistsDraftRejectsStaleWritesAndReplaysSubmission(){
        complete(now.minus(Duration.ofDays(7)));deterministic.start(userId,id);
        var draft=deterministic.save(userId,id,new DelayedRecallService.Response(0L,"부산에서 친구와 케이크를 먹었다",false),false);
        assertThat(draft.version()).isEqualTo(1);assertThat(service.get(userId,id).responseText()).contains("케이크");
        assertThatThrownBy(()->deterministic.save(userId,id,new DelayedRecallService.Response(0L,"변경",false),false)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        var request=new DelayedRecallService.Response(1L,draft.responseText(),false);
        assertThat(deterministic.save(userId,id,request,true).status()).isEqualTo("SUBMITTED");
        assertThat(deterministic.save(userId,id,request,true).status()).isEqualTo("SUBMITTED");
        assertThatThrownBy(()->deterministic.save(userId,id,new DelayedRecallService.Response(2L,"수정",false),false)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM results WHERE training_session_id=?",Integer.class,sessionId)).isZero();
    }
    @Test void serverRejectsEarlyAndForeignRequests() throws Exception {
        complete(Instant.now());
        for(String suffix:List.of("/start","/submit")) mvc.perform(post(path()+suffix).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("version",0,"text","기억","cannotRecall",false)))).andExpect(status().isConflict());
        mvc.perform(get(path()).header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }
    @Test void fixedFactsCannotBeOverwrittenOrEnrolledAfterCompletion() throws Exception {
        assertThat(deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(references,true)).id()).isEqualTo(id);
        assertThatThrownBy(()->deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(List.of(new DelayedRecallService.PhotoFact(photoId,"변경")),true))).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        mvc.perform(post("/api/users/me/training-sessions/"+sessionId+"/delayed-recall").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"facts\":[null],\"confirmed\":true}")).andExpect(status().isBadRequest());
        db.update("DELETE FROM delayed_recalls WHERE id=?",id);complete(now);
        assertThatThrownBy(()->deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(references,true))).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
    }
    @Test void rejectsRepeatedPhotosAndMultilineInformation(){
        for(var facts:List.of(List.of(new DelayedRecallService.PhotoFact(photoId,"첫 줄"),new DelayedRecallService.PhotoFact(photoId,"다른 줄")),
                List.of(new DelayedRecallService.PhotoFact(photoId,"첫 줄\n둘째 줄")))) {
            assertThatThrownBy(()->deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(facts,true)))
                .isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        }
    }
    @Test void retiredManualGradingEndpointsAreAbsent() throws Exception {
        for(String endpoint:List.of("/api/users/me/delayed-recalls/access","/api/review/delayed-recalls","/api/review/delayed-recalls/"+id))
            mvc.perform(get(endpoint).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void historicalScoresAndFactsRemainReadableWithoutAccountLink(){
        db.update("UPDATE delayed_recall_facts SET recalled=(fact_order=1) WHERE delayed_recall_id=?",id);
        db.update("UPDATE delayed_recalls SET graded_at=? WHERE id=?",Timestamp.from(now),id);
        assertThat(service.get(userId,id).recallRate()).isEqualTo(50.0);
        assertThat(service.get(userId,id).facts()).hasSize(2);
    }
    @Test void deletingAnotherReferencePhotoKeepsItsInformationSnapshot(){
        db.update("DELETE FROM photos WHERE id=?",secondPhotoId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM delayed_recall_facts WHERE delayed_recall_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT photo_id FROM delayed_recall_facts WHERE delayed_recall_id=? AND fact_order=2",Long.class,id)).isNull();
        assertThat(db.queryForObject("SELECT content FROM delayed_recall_facts WHERE delayed_recall_id=? AND fact_order=2",String.class,id)).isEqualTo("친구와 케이크를 먹었다");
    }
    @Test void cannotRecallCanBeSubmittedAndSessionDeletionCascades(){
        complete(now.minus(Duration.ofDays(7)));deterministic.start(userId,id);
        assertThatThrownBy(()->deterministic.save(userId,id,new DelayedRecallService.Response(0L,"",false),true)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        assertThat(deterministic.save(userId,id,new DelayedRecallService.Response(0L,"",true),true).status()).isEqualTo("SUBMITTED");
        db.update("DELETE FROM training_sessions WHERE id=?",sessionId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM delayed_recall_facts WHERE delayed_recall_id=?",Integer.class,id)).isZero();
    }

    // AI 채점 테스트용 평가 제출
    private void submitForGrading(String text, boolean cannotRecall) {
        complete(now.minus(Duration.ofDays(7)));
        deterministic.start(userId, id);

        deterministic.save(
                userId,
                id,
                new DelayedRecallService.Response(
                        0L, text, cannotRecall
                ),
                true
        );
    }

    // 채점 대상 사실 ID 조회
    private List<Long> gradingFactIds() {
        return db.queryForList(
                "SELECT id FROM delayed_recall_facts " +
                        "WHERE delayed_recall_id=? ORDER BY fact_order",
                Long.class,
                id
        );
    }

    // 테스트 1. 모든 정보를 기억한 경우
    @Test
    void aiGradingReturnsFullScore() {
        submitForGrading(
                "부산에 갔고 친구와 케이크를 먹었다",
                false
        );

        var ids = gradingFactIds();

        when(gradingClient.grade(anyList(), anyString()))
                .thenReturn(List.of(
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(0), true,
                                "부산에 갔고",
                                "장소를 정확하게 기억함"
                        ),
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(1), true,
                                "친구와 케이크를 먹었다",
                                "경험을 정확하게 기억함"
                        )
                ));

        var result = gradingService.grade(userId, id);

        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(result.recallRate()).isEqualTo(100.0);
        assertThat(result.recalledCount()).isEqualTo(2);
        assertThat(result.facts()).hasSize(2);
    }

    // 테스트 2. 일부 정보만 기억한 경우
    @Test
    void aiGradingReturnsPartialScore() {
        submitForGrading("부산에 갔다", false);

        var ids = gradingFactIds();

        when(gradingClient.grade(anyList(), anyString()))
                .thenReturn(List.of(
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(0), true,
                                "부산에 갔다",
                                "장소를 정확하게 기억함"
                        ),
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(1), false,
                                "",
                                "케이크를 먹은 경험이 없음"
                        )
                ));

        var result = gradingService.grade(userId, id);

        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(result.recallRate()).isEqualTo(50.0);
        assertThat(result.recalledCount()).isEqualTo(1);
    }

    // 테스트 3. 잘못된 정보를 기억한 경우
    @Test
    void aiGradingReturnsZeroScore() {
        submitForGrading("제주도에서 혼자 밥을 먹었다", false);

        var ids = gradingFactIds();

        when(gradingClient.grade(anyList(), anyString()))
                .thenReturn(List.of(
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(0), false, "",
                                "장소가 일치하지 않음"
                        ),
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(1), false, "",
                                "경험이 일치하지 않음"
                        )
                ));

        var result = gradingService.grade(userId, id);

        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(result.recallRate()).isEqualTo(0.0);
        assertThat(result.recalledCount()).isZero();
    }

    // 테스트 4. 기억나지 않음을 선택한 경우
    @Test
    void cannotRecallSkipsGemini() {
        submitForGrading("", true);

        var result = gradingService.grade(userId, id);

        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(result.recallRate()).isEqualTo(0.0);

        verifyNoInteractions(gradingClient);
    }

    // 테스트 5. AI 오류 발생 후 재시도
    @Test
    void aiFailurePreservesResponseAndAllowsRetry() {
        submitForGrading("부산에 갔다", false);

        when(gradingClient.grade(anyList(), anyString()))
                .thenThrow(new RuntimeException("AI 서버 오류"));

        assertThatThrownBy(
                () -> gradingService.grade(userId, id)
        ).isInstanceOf(RuntimeException.class);

        var saved = service.get(userId, id);

        assertThat(saved.status()).isEqualTo("SUBMITTED");
        assertThat(saved.responseText()).isEqualTo("부산에 갔다");

        var ids = gradingFactIds();

        doReturn(List.of(
                new GeminiRecallGradingClient.FactGrade(
                        ids.get(0), true,
                        "부산에 갔다",
                        "장소 일치"
                ),
                new GeminiRecallGradingClient.FactGrade(
                        ids.get(1), false, "",
                        "경험 누락"
                )
        )).when(gradingClient).grade(anyList(), anyString());

        var result = gradingService.grade(userId, id);

        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(result.recallRate()).isEqualTo(50.0);
    }

    // 테스트 6. 중복 채점 방지
    @Test
    void repeatedGradingDoesNotCallGeminiAgain() {
        submitForGrading("부산에 갔다", false);

        var ids = gradingFactIds();

        when(gradingClient.grade(anyList(), anyString()))
                .thenReturn(List.of(
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(0), true,
                                "부산에 갔다",
                                "장소 일치"
                        ),
                        new GeminiRecallGradingClient.FactGrade(
                                ids.get(1), false, "",
                                "경험 누락"
                        )
                ));

        var first = gradingService.grade(userId, id);
        var second = gradingService.grade(userId, id);

        assertThat(second.recallRate())
                .isEqualTo(first.recallRate());

        assertThat(second.gradedAt())
                .isEqualTo(first.gradedAt());

        verify(gradingClient, times(1))
                .grade(anyList(), anyString());
    }

    // 테스트 7. 다른 사용자의 채점 요청 차단
    @Test
    void foreignUserCannotRequestGrading() {
        submitForGrading("부산에 갔다", false);

        assertThatThrownBy(
                () -> gradingService.grade(otherId, id)
        ).isInstanceOf(
                com.konkuk.memory.exception.ApiException.class
        );

        verifyNoInteractions(gradingClient);
    }
}
