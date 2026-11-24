package com.konkuk.memory.service;

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
},mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class DelayedRecallIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.konkuk.memory.entity")
    @EnableJpaRepositories("com.konkuk.memory.repository")
    @Import({DelayedRecallService.class, DelayedRecallController.class, SecurityConfig.class,
        SecurityErrorHandler.class, RevokedTokenStore.class, RevokedTokenRepository.class, TokenService.class, GlobalExceptionHandler.class})
    static class TestApp {}
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
    long userId,otherId,sessionId,photoId,id;
    String token,otherToken;
    final Instant now=Instant.parse("2026-11-24T12:00:00Z");
    DelayedRecallService deterministic;
    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString();
        var owner=users.saveAndFlush(new User(suffix+"@example.com","fixture","본인"));
        var other=users.saveAndFlush(new User("other"+suffix+"@example.com","fixture","담당자"));
        userId=owner.getId();otherId=other.getId();token=tokens.issue(userId).accessToken();otherToken=tokens.issue(otherId).accessToken();
        var album=albums.saveAndFlush(new Album(owner,"노출하면 안 되는 앨범 제목"));
        var photo=photos.saveAndFlush(new Photo(owner,album,suffix,"노출하면 안 되는 사진.jpg","image/jpeg",1));photoId=photo.getId();
        var s=new TrainingSession(photo);s.markInProgress();sessionId=sessions.saveAndFlush(s).getId();
        deterministic=new DelayedRecallService(db,sessions,Long.toString(otherId),Clock.fixed(now,ZoneOffset.UTC));
        id=deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(List.of("부산에 갔다","친구와 케이크를 먹었다"),true)).id();
    }
    void complete(Instant at){entityManager.flush();db.update("UPDATE training_sessions SET started_at=?,completed_at=?,status='COMPLETED' WHERE id=?",Timestamp.from(at.minusSeconds(100)),Timestamp.from(at),sessionId);entityManager.clear();}
    String path(){return "/api/users/me/delayed-recalls/"+id;}
    String body(Object x)throws Exception{return json.writeValueAsString(x);}
    @Test void exactSevenDayBoundaryAndNoReferenceLeak() throws Exception {
        complete(now.minus(Duration.ofDays(7)).plusNanos(1000));
        assertThat(deterministic.get(userId,id).status()).isEqualTo("NOT_DUE");
        assertThatThrownBy(()->deterministic.start(userId,id)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        complete(now.minus(Duration.ofDays(7)));
        assertThat(deterministic.get(userId,id).status()).isEqualTo("AVAILABLE");
        assertThat(deterministic.start(userId,id).status()).isEqualTo("IN_PROGRESS");
        assertThat(deterministic.get(userId,id).facts()).isEmpty();
        String result=mvc.perform(get(path()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(result).doesNotContain("부산","케이크","앨범 제목","사진.jpg");
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
        mvc.perform(patch(path()+"/draft").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("version",0,"text","기억","cannotRecall",false)))).andExpect(status().isConflict());
        mvc.perform(get(path()).header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path()+"/start").header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/review/delayed-recalls/"+id).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }
    @Test void fixedFactsCannotBeOverwrittenOrEnrolledAfterCompletion() throws Exception {
        assertThat(deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(List.of("부산에 갔다","친구와 케이크를 먹었다"),true)).id()).isEqualTo(id);
        assertThatThrownBy(()->deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(List.of("변경"),true))).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        mvc.perform(post("/api/users/me/training-sessions/"+sessionId+"/delayed-recall").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"facts\":[null],\"confirmed\":true}")).andExpect(status().isBadRequest());
        db.update("DELETE FROM delayed_recalls WHERE id=?",id);complete(now);
        assertThatThrownBy(()->deterministic.setup(userId,sessionId,new DelayedRecallService.Setup(List.of("사후 확인"),true))).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
    }
    @Test void gradingRequiresAllFactsRealQuotesAndIndependentReviewer(){
        complete(now.minus(Duration.ofDays(7)));deterministic.start(userId,id);
        deterministic.save(userId,id,new DelayedRecallService.Response(0L,"부산에 갔다",false),true);
        var facts=deterministic.review(otherId,id).facts();
        assertThatThrownBy(()->deterministic.review(userId,id)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        var incomplete=new DelayedRecallService.Grade(List.of(new DelayedRecallService.Mark(facts.get(0).id(),true,"부산에 갔다","")));
        assertThatThrownBy(()->deterministic.grade(otherId,id,incomplete)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        var invalid=new DelayedRecallService.Grade(List.of(new DelayedRecallService.Mark(facts.get(0).id(),true,"제주도",""),new DelayedRecallService.Mark(facts.get(1).id(),false,"","")));
        assertThatThrownBy(()->deterministic.grade(otherId,id,invalid)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        var grade=new DelayedRecallService.Grade(List.of(new DelayedRecallService.Mark(facts.get(0).id(),true,"부산에 갔다","의미 일치"),new DelayedRecallService.Mark(facts.get(1).id(),false,"","언급 없음")));
        var result=deterministic.grade(otherId,id,grade);
        assertThat(result.recallRate()).isEqualTo(50.0);assertThat(service.get(userId,id).facts()).hasSize(2);
        assertThat(result.elapsedSeconds()).isEqualTo(604800L);
        assertThatThrownBy(()->deterministic.grade(otherId,id,grade)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
    }
    @Test void cannotRecallCanBeSubmittedAndSessionDeletionCascades(){
        complete(now.minus(Duration.ofDays(7)));deterministic.start(userId,id);
        assertThatThrownBy(()->deterministic.save(userId,id,new DelayedRecallService.Response(0L,"",false),true)).isInstanceOf(com.konkuk.memory.exception.ApiException.class);
        deterministic.save(userId,id,new DelayedRecallService.Response(0L,"",true),true);
        var marks=deterministic.review(otherId,id).facts().stream().map(f->new DelayedRecallService.Mark(f.id(),false,"","기억 없음")).toList();
        assertThat(deterministic.grade(otherId,id,new DelayedRecallService.Grade(marks)).recallRate()).isZero();
        db.update("DELETE FROM training_sessions WHERE id=?",sessionId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM delayed_recall_facts WHERE delayed_recall_id=?",Integer.class,id)).isZero();
    }
}
