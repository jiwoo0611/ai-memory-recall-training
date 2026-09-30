package com.konkuk.memory.service;

import com.konkuk.memory.dto.RecallSubmissionRequest;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RecallService {
    private final TrainingSessionRepository sessions;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final ResultRepository results;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public RecallService(TrainingSessionRepository sessions, QuestionRepository questions,
                         AnswerRepository answers, ResultRepository results, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.sessions = sessions; this.questions = questions; this.answers = answers; this.results = results;
        this.jdbc = jdbc;
    }

    @Transactional
    public void submit(Long userId, Long sessionId, RecallSubmissionRequest request) {
        var session = locked(userId, sessionId);
        var owned = questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(sessionId, userId);
        Map<Long, RecallSubmissionRequest.Item> submitted = new HashMap<>();
        for (var item : request.answers()) {
            String content = item.content() == null ? null : item.content().strip();
            if (item.status() == null || (item.status() == Answer.Status.ANSWERED
                    ? content == null || content.isBlank() || content.length() > 2000
                    : content != null && !content.isEmpty())) throw invalid("답변은 1~2000자로 입력하고, 건너뛰기를 선택하면 답변 내용을 비워주세요.");
            var normalized = new RecallSubmissionRequest.Item(item.questionId(), item.status(),
                    item.status() == Answer.Status.ANSWERED ? content : null);
            if (submitted.putIfAbsent(item.questionId(), normalized) != null) throw invalid("질문이 중복되었습니다.");
        }
        if (owned.isEmpty() || owned.size() != submitted.size()
                || owned.stream().anyMatch(q -> !submitted.containsKey(q.getId()))) {
            throw invalid("이 훈련의 모든 질문에 답변하거나 건너뛰기 상태를 선택해주세요.");
        }
        var stored = answers.findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(sessionId, userId);
        if (session.getStatus() == TrainingSession.Status.COMPLETED) {
            boolean same = stored.size() == submitted.size() && stored.stream().allMatch(a -> {
                var item = submitted.get(a.getQuestion().getId());
                return item != null && item.status() == a.getStatus() && Objects.equals(item.content(), a.getContent());
            });
            if (same) return; // A committed request can be replayed after a lost HTTP response.
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "이미 저장된 답변입니다. 새 훈련을 시작해주세요.");
        }
        if (session.getStatus() != TrainingSession.Status.IN_PROGRESS || !stored.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_READY", "답변을 제출할 수 없는 훈련 상태입니다.");
        }
        for (var q : owned) {
            var item = submitted.get(q.getId());
            answers.save(new Answer(q, item.status(), item.content()));
        }
        Result result = new Result(session);
        result.setCounts((int) submitted.values().stream().filter(a -> a.status() == Answer.Status.ANSWERED).count(),
                (int) submitted.values().stream().filter(a -> a.status() == Answer.Status.CANNOT_RECALL).count(),
                (int) submitted.values().stream().filter(a -> a.status() == Answer.Status.QUESTION_MISMATCH).count());
        results.save(result);
        session.markCompleted();
        answers.flush();
    }

    @Transactional
    public Long retrain(Long userId, Long sourceId, String requestId) {
        var source = locked(userId, sourceId);
        var previous = sessions.findBySourceSessionIdAndRetryKey(sourceId, requestId);
        if (previous.isPresent()) return previous.get().getId();
        var originals = questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(sourceId, userId);
        if (originals.isEmpty()) throw invalid("재훈련할 질문이 없습니다.");
        var next = new TrainingSession(source.getPhoto());
        next.markInProgress();
        sessions.saveAndFlush(next);
        jdbc.update("INSERT INTO training_retries(source_session_id, request_id, target_session_id) VALUES (?,?,?)", sourceId, requestId, next.getId());
        for (var q : originals) questions.save(new Question(next, q.getQuestionOrder(), q.getContent(), q.getType(),
                q.getEvidenceSnapshot(), q.isVerifiable(), q.getReferenceInformation(), q.getGenerationVersion(),
                q.getFormat(), q.getOptionsJson(), q.getAnswerKey()));
        return next.getId();
    }

    private TrainingSession locked(Long userId, Long id) {
        return sessions.lockOwned(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRAINING_SESSION_NOT_FOUND", "훈련 기록을 찾을 수 없습니다."));
    }
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECALL_ANSWERS", message);
    }
}
