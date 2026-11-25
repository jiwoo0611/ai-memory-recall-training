package com.konkuk.memory.service;

import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.TrainingSessionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Independent free-recall protocol. Never changes scored quiz answers or results. */
@Service
@Transactional
public class DelayedRecallService {
    public record PhotoFact(@NotNull @Positive Long photoId, @NotBlank @Size(max=500) String content) {}
    public record Setup(@NotEmpty @Size(max=100) List<@NotNull @Valid PhotoFact> facts,
                        @AssertTrue boolean confirmed) {}
    public record Response(@NotNull @PositiveOrZero Long version, @NotNull @Size(max=10000) String text,
                           @NotNull Boolean cannotRecall) {}
    public record Fact(Long id, int order, String content, Boolean recalled, String evidenceQuote, String note, Long photoId) {}
    public record View(Long id, Long sourceSessionId, String status, String condition, String protocolVersion,
                       Instant confirmedAt, Instant completedAt, Instant availableAt, Instant startedAt,
                       Instant submittedAt, Long elapsedSeconds, String responseText, boolean cannotRecall,
                       long version, int factCount, Integer recalledCount, Double recallRate,
                       Instant gradedAt, List<Fact> facts) {}
    private record Row(long id, long sessionId, long ownerId, String condition, String protocol,
                       Instant confirmed, Instant completed, Instant started, Instant submitted,
                       String text, boolean cannotRecall, long version, Instant graded) {}
    private final JdbcTemplate db;
    private final TrainingSessionRepository sessions;
    private final PhotoRepository photos;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DelayedRecallService(JdbcTemplate db, TrainingSessionRepository sessions, PhotoRepository photos) {
        this(db, sessions, photos, Clock.systemUTC());
    }
    // Exact seven-day boundary remains testable without a production bypass.
    DelayedRecallService(JdbcTemplate db, TrainingSessionRepository sessions, PhotoRepository photos, Clock clock) {
        this.db=db; this.sessions=sessions; this.photos=photos; this.clock=clock;
    }
    private static Instant instant(ResultSet r, String name) throws SQLException {
        Timestamp t=r.getTimestamp(name); return t==null?null:t.toInstant();
    }
    private List<Row> rows(String where, Object... args) {
        return db.query("SELECT d.*, s.completed_at, p.user_id FROM delayed_recalls d "
            +"JOIN training_sessions s ON s.id=d.source_session_id JOIN photos p ON p.id=s.photo_id "+where,
            (r,n)->new Row(r.getLong("id"),r.getLong("source_session_id"),r.getLong("user_id"),
                r.getString("condition_name"),r.getString("protocol_version"),instant(r,"confirmed_at"),
                instant(r,"completed_at"),instant(r,"started_at"),instant(r,"submitted_at"),
                r.getString("response_text"),r.getBoolean("cannot_recall"),r.getLong("draft_version"),instant(r,"graded_at")),args);
    }
    private Row owned(long userId,long id,boolean lock) {
        var found=rows("WHERE d.id=? AND p.user_id=?"+(lock?" FOR UPDATE":""),id,userId);
        if(found.isEmpty()) throw missing(); return found.get(0);
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"DELAYED_RECALL_NOT_FOUND","회상 평가를 찾을 수 없습니다."); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,"DELAYED_RECALL_CONFLICT",message); }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DELAYED_RECALL",message); }
    private Instant due(Row r) { return r.completed==null?null:r.completed.plus(Duration.ofDays(7)); }
    private String status(Row r) {
        if(r.graded!=null) return "GRADED";
        if(r.submitted!=null) return "SUBMITTED";
        if(r.completed==null) return "WAITING_TRAINING";
        if(clock.instant().isBefore(due(r))) return "NOT_DUE";
        return r.started==null?"AVAILABLE":"IN_PROGRESS";
    }
    private List<Fact> facts(long id) {
        return db.query("SELECT * FROM delayed_recall_facts WHERE delayed_recall_id=? ORDER BY fact_order",
            (r,n)->new Fact(r.getLong("id"),r.getInt("fact_order"),r.getString("content"),
                (Boolean)r.getObject("recalled"),r.getString("evidence_quote"),r.getString("grade_note"),(Long)r.getObject("photo_id")),id);
    }
    private View view(Row r, boolean detail) {
        List<Fact> all=facts(r.id);
        Integer count=r.graded==null?null:(int)all.stream().filter(f->Boolean.TRUE.equals(f.recalled)).count();
        Double rate=count==null||all.isEmpty()?null:Math.round(count*1000.0/all.size())/10.0;
        // Before final grading the participant never receives facts or grading notes over the wire.
        List<Fact> visible=detail&&r.graded!=null?all:List.of();
        return new View(r.id,r.sessionId,status(r),r.condition,r.protocol,r.confirmed,r.completed,due(r),r.started,r.submitted,
            r.completed==null||r.submitted==null?null:Duration.between(r.completed,r.submitted).getSeconds(),
            detail?r.text:null,r.cannotRecall,r.version,all.size(),count,rate,r.graded,visible);
    }
    @Transactional(readOnly=true)
    public List<View> list(long userId) {
        return rows("WHERE p.user_id=? ORDER BY d.id DESC",userId).stream().map(r->view(r,false)).toList();
    }
    @Transactional(readOnly=true)
    public List<View> listForAlbum(long userId, long albumId) {
        return rows("WHERE p.user_id=? AND p.album_id=? ORDER BY d.id DESC",userId,albumId)
            .stream().map(r->view(r,false)).toList();
    }
    @Transactional(readOnly=true)
    public View get(long userId,long id) { return view(owned(userId,id,false),true); }
    @Transactional(readOnly=true)
    public View forSession(long userId,long sessionId) {
        sessions.findByIdAndPhoto_User_Id(sessionId,userId).orElseThrow(DelayedRecallService::missing);
        var found=rows("WHERE d.source_session_id=? AND p.user_id=?",sessionId,userId);
        return found.isEmpty()?null:view(found.get(0),false);
    }
    public View setup(long userId,long sessionId,Setup request) {
        var session=sessions.lockOwned(sessionId,userId).orElseThrow(DelayedRecallService::missing);
        var original=rows("WHERE d.source_session_id=?",sessionId);
        if(!request.confirmed() || request.facts()==null || request.facts().isEmpty() || request.facts().size()>100)
            throw invalid("사진별 정보를 입력하고 실제 경험과 일치하는지 확인해주세요.");
        List<PhotoFact> normalized=new ArrayList<>();
        Set<Long> photoIds=new HashSet<>();
        Set<String> contents=new HashSet<>();
        long albumId=session.getPhoto().getAlbum().getId();
        Set<Long> allowed=new HashSet<>();
        photos.findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(albumId,userId).forEach(p->allowed.add(p.getId()));
        for(var fact:request.facts()) {
            if(fact==null || fact.photoId()==null || !allowed.contains(fact.photoId()))
                throw invalid("이 훈련의 앨범에 업로드한 사진만 등록할 수 있습니다.");
            String content=fact.content()==null?"":fact.content().strip();
            if(content.isBlank() || content.length()>500 || content.contains("\n") || content.contains("\r")
                    || !photoIds.add(fact.photoId()) || !contents.add(content))
                throw invalid("각 사진에 서로 다른 정보를 한 줄씩 입력해주세요. 한 줄은 500자 이내입니다.");
            normalized.add(new PhotoFact(fact.photoId(),content));
        }
        if(!original.isEmpty()) {
            var row=original.get(0);
            var saved=facts(row.id).stream().map(f->new PhotoFact(f.photoId(),f.content())).toList();
            if(!saved.equals(normalized)) throw conflict("확인된 사진별 정보는 변경할 수 없습니다.");
            return view(row,false);
        }
        if(session.getStatus()!=TrainingSession.Status.IN_PROGRESS) throw conflict("사진별 정보는 훈련 전에 등록해주세요.");
        db.update("INSERT INTO delayed_recalls(source_session_id,confirmed_at) VALUES (?,?)",sessionId,Timestamp.from(clock.instant()));
        Row row=rows("WHERE d.source_session_id=?",sessionId).get(0);
        for(int i=0;i<normalized.size();i++) {
            var fact=normalized.get(i);
            db.update("INSERT INTO delayed_recall_facts(delayed_recall_id,fact_order,photo_id,content) VALUES (?,?,?,?)",
                row.id,i+1,fact.photoId(),fact.content());
        }
        return view(row,false);
    }
    private void available(Row r) {
        if(r.completed==null||clock.instant().isBefore(due(r))) throw conflict("훈련 완료부터 7일 후에 평가를 시작할 수 있습니다.");
    }
    public View start(long userId,long id) {
        Row r=owned(userId,id,true); available(r);
        if(r.started==null&&r.submitted==null) db.update("UPDATE delayed_recalls SET started_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
        return get(userId,id);
    }
    public View save(long userId,long id,Response response,boolean submit) {
        Row r=owned(userId,id,true); available(r);
        String text=response.text.strip();
        if(response.cannotRecall&&!text.isEmpty()) throw invalid("기억나는 내용이 없으면 입력 내용을 비워주세요.");
        if(submit&&!response.cannotRecall&&text.isEmpty()) throw invalid("기억나는 내용을 적거나 기억나지 않음을 선택해주세요.");
        if(r.submitted!=null) {
            if(submit&&Objects.equals(r.text,text)&&r.cannotRecall==response.cannotRecall) return view(r,true);
            throw conflict("제출한 답변은 수정할 수 없습니다.");
        }
        if(r.started==null) throw conflict("평가 안내를 확인하고 먼저 시작해주세요.");
        if(r.version!=response.version) throw conflict("다른 창에서 답변이 변경되었습니다. 최신 내용을 불러온 뒤 다시 저장해주세요.");
        db.update("UPDATE delayed_recalls SET response_text=?, cannot_recall=?, draft_version=draft_version+1, submitted_at=? WHERE id=?",
            text,response.cannotRecall,submit?Timestamp.from(clock.instant()):null,id);
        return get(userId,id);
    }
}
