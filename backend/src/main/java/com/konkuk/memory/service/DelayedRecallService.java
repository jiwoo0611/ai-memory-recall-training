package com.konkuk.memory.service;

import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.TrainingSessionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
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
    public record Setup(@NotEmpty @Size(max=30) List<@NotBlank @Size(max=500) String> facts,
                        @AssertTrue boolean confirmed) {}
    public record Response(@NotNull @PositiveOrZero Long version, @NotNull @Size(max=10000) String text,
                           @NotNull Boolean cannotRecall) {}
    public record Mark(@NotNull Long factId, @NotNull Boolean recalled,
                       @Size(max=2000) String evidenceQuote, @Size(max=1000) String note) {}
    public record Grade(@NotEmpty @Size(max=30) List<@NotNull @Valid Mark> marks) {}
    public record Fact(Long id, int order, String content, Boolean recalled, String evidenceQuote, String note) {}
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
    private final Set<Long> reviewers;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DelayedRecallService(JdbcTemplate db, TrainingSessionRepository sessions,
            @Value("${app.recall.reviewer-user-ids:}") String reviewerIds) {
        this(db, sessions, reviewerIds, Clock.systemUTC());
    }
    // Explicit clock makes the exact seven-day boundary testable without a production bypass.
    DelayedRecallService(JdbcTemplate db, TrainingSessionRepository sessions, String ids, Clock clock) {
        this.db=db; this.sessions=sessions; this.clock=clock;
        Set<Long> allowed=new HashSet<>();
        for (String id : ids.split(",")) if (!id.isBlank()) allowed.add(Long.valueOf(id.trim()));
        this.reviewers=Set.copyOf(allowed);
    }
    public boolean canReview(long userId) { return reviewers.contains(userId); }
    private void reviewer(long userId) {
        if (!canReview(userId)) throw new ApiException(HttpStatus.FORBIDDEN,"REVIEW_FORBIDDEN","채점 담당자만 접근할 수 있습니다.");
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
                (Boolean)r.getObject("recalled"),r.getString("evidence_quote"),r.getString("grade_note")),id);
    }
    private View view(Row r, boolean review, boolean detail) {
        List<Fact> all=facts(r.id);
        Integer count=r.graded==null?null:(int)all.stream().filter(f->Boolean.TRUE.equals(f.recalled)).count();
        Double rate=count==null||all.isEmpty()?null:Math.round(count*1000.0/all.size())/10.0;
        // Before final grading the participant never receives facts or grading notes over the wire.
        List<Fact> visible=detail&&(review||r.graded!=null)?all:List.of();
        return new View(r.id,r.sessionId,status(r),r.condition,r.protocol,r.confirmed,r.completed,due(r),r.started,r.submitted,
            r.completed==null||r.submitted==null?null:Duration.between(r.completed,r.submitted).getSeconds(),
            detail?r.text:null,r.cannotRecall,r.version,all.size(),count,rate,r.graded,visible);
    }
    @Transactional(readOnly=true)
    public List<View> list(long userId) {
        return rows("WHERE p.user_id=? ORDER BY d.id DESC",userId).stream().map(r->view(r,false,false)).toList();
    }
    @Transactional(readOnly=true)
    public View get(long userId,long id) { return view(owned(userId,id,false),false,true); }
    @Transactional(readOnly=true)
    public View forSession(long userId,long sessionId) {
        sessions.findByIdAndPhoto_User_Id(sessionId,userId).orElseThrow(DelayedRecallService::missing);
        var found=rows("WHERE d.source_session_id=? AND p.user_id=?",sessionId,userId);
        return found.isEmpty()?null:view(found.get(0),false,false);
    }
    public View setup(long userId,long sessionId,Setup request) {
        var session=sessions.lockOwned(sessionId,userId).orElseThrow(DelayedRecallService::missing);
        var original=rows("WHERE d.source_session_id=?",sessionId);
        List<String> normalized=request.facts.stream().map(String::strip).toList();
        if(!request.confirmed||normalized.isEmpty()||normalized.stream().anyMatch(String::isBlank)
                ||new HashSet<>(normalized).size()!=normalized.size()) throw invalid("중복되지 않는 확인된 사실을 입력해주세요.");
        if(!original.isEmpty()) {
            var row=original.get(0);
            if(!facts(row.id).stream().map(Fact::content).toList().equals(normalized)) throw conflict("확인된 기준 사실은 변경할 수 없습니다.");
            return view(row,false,false);
        }
        if(session.getStatus()!=TrainingSession.Status.IN_PROGRESS) throw conflict("기준 사실은 훈련 전에 등록해주세요.");
        db.update("INSERT INTO delayed_recalls(source_session_id,confirmed_at) VALUES (?,?)",sessionId,Timestamp.from(clock.instant()));
        Row row=rows("WHERE d.source_session_id=?",sessionId).get(0);
        for(int i=0;i<normalized.size();i++) db.update("INSERT INTO delayed_recall_facts(delayed_recall_id,fact_order,content) VALUES (?,?,?)",row.id,i+1,normalized.get(i));
        return view(row,false,false);
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
            if(submit&&Objects.equals(r.text,text)&&r.cannotRecall==response.cannotRecall) return view(r,false,true);
            throw conflict("제출한 답변은 수정할 수 없습니다.");
        }
        if(r.started==null) throw conflict("평가 안내를 확인하고 먼저 시작해주세요.");
        if(r.version!=response.version) throw conflict("다른 창에서 답변이 변경되었습니다. 최신 내용을 불러온 뒤 다시 저장해주세요.");
        db.update("UPDATE delayed_recalls SET response_text=?, cannot_recall=?, draft_version=draft_version+1, submitted_at=? WHERE id=?",
            text,response.cannotRecall,submit?Timestamp.from(clock.instant()):null,id);
        return get(userId,id);
    }
    @Transactional(readOnly=true)
    public List<View> queue(long userId) {
        reviewer(userId);
        return rows("WHERE d.submitted_at IS NOT NULL AND d.graded_at IS NULL AND p.user_id<>? ORDER BY d.id",userId)
            .stream().map(r->view(r,true,false)).toList();
    }
    @Transactional(readOnly=true)
    public View review(long userId,long id) {
        reviewer(userId);
        Row r=rows("WHERE d.id=?",id).stream().findFirst().orElseThrow(DelayedRecallService::missing);
        if(r.ownerId==userId) throw new ApiException(HttpStatus.FORBIDDEN,"SELF_GRADING_FORBIDDEN","자신의 응답은 채점할 수 없습니다.");
        if(r.submitted==null) throw conflict("제출된 응답만 채점할 수 있습니다.");
        return view(r,true,true);
    }
    public View grade(long userId,long id,Grade grade) {
        reviewer(userId);
        Row r=rows("WHERE d.id=? FOR UPDATE",id).stream().findFirst().orElseThrow(DelayedRecallService::missing);
        review(userId,id);
        if(r.graded!=null) throw conflict("이미 확정된 채점입니다.");
        var all=facts(id); Map<Long,Mark> marks=new HashMap<>();
        for(var m:grade.marks) if(marks.putIfAbsent(m.factId,m)!=null) throw invalid("중복된 사실 판정입니다.");
        if(all.size()!=marks.size()||all.stream().anyMatch(f->!marks.containsKey(f.id))) throw invalid("모든 기준 사실을 한 번씩 판정해주세요.");
        for(var f:all) {
            var m=marks.get(f.id); String quote=m.evidenceQuote==null?"":m.evidenceQuote.strip();
            if(m.recalled&&(r.cannotRecall||quote.isBlank()||!r.text.contains(quote))) throw invalid("회상 인정 시 응답에 실제로 포함된 근거 문장을 입력해주세요.");
            db.update("UPDATE delayed_recall_facts SET recalled=?,evidence_quote=?,grade_note=? WHERE id=?",m.recalled,quote,m.note,f.id);
        }
        db.update("UPDATE delayed_recalls SET graded_at=?,grader_user_id=? WHERE id=?",Timestamp.from(clock.instant()),userId,id);
        return review(userId,id);
    }
}
