
package com.konkuk.memory.service;

import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.service.GeminiRecallGradingClient.FactGrade;
import com.konkuk.memory.service.GeminiRecallGradingClient.ReferenceFact;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class DelayedRecallGradingService {

    private final JdbcTemplate db;
    private final GeminiRecallGradingClient gradingClient;
    private final DelayedRecallService delayedRecallService;
    private final TransactionTemplate transactionTemplate;

    public DelayedRecallGradingService(
            JdbcTemplate db,
            GeminiRecallGradingClient gradingClient,
            DelayedRecallService delayedRecallService,
            TransactionTemplate transactionTemplate
    ) {
        this.db = db;
        this.gradingClient = gradingClient;
        this.delayedRecallService = delayedRecallService;
        this.transactionTemplate = transactionTemplate;
    }

    private record Evaluation(
            long id,
            String responseText,
            boolean cannotRecall,
            boolean submitted,
            boolean graded
    ) {}

    private record GradingInput(
            Evaluation evaluation,
            List<ReferenceFact> facts
    ) {}

    public DelayedRecallService.View grade(
            long userId,
            long evaluationId
    ) {

        // 1. 채점에 필요한 데이터 조회
        GradingInput input = transactionTemplate.execute(status -> {

            Evaluation evaluation =
                    findOwned(userId, evaluationId, false);

            if (!evaluation.submitted()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DELAYED_RECALL_NOT_SUBMITTED",
                        "평가 답변을 먼저 제출해주세요."
                );
            }

            if (evaluation.graded()) {
                return new GradingInput(
                        evaluation,
                        List.of()
                );
            }

            List<ReferenceFact> facts =
                    findFacts(evaluationId);

            if (facts.isEmpty()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DELAYED_RECALL_FACTS_NOT_FOUND",
                        "채점할 경험 정보가 없습니다."
                );
            }

            return new GradingInput(evaluation, facts);
        });

        if (input == null) {
            throw new IllegalStateException(
                    "채점 데이터를 조회하지 못했습니다."
            );
        }

        // 2. 이미 채점된 평가라면 기존 결과 반환
        if (input.evaluation().graded()) {
            return delayedRecallService.get(
                    userId,
                    evaluationId
            );
        }

        List<FactGrade> grades;

        // 3. 기억나지 않음을 선택한 경우
        if (input.evaluation().cannotRecall()) {

            grades = input.facts().stream()
                    .map(fact -> new FactGrade(
                            fact.factId(),
                            false,
                            "",
                            "사용자가 기억나지 않음을 선택했습니다."
                    ))
                    .toList();

        } else {

            // 4. 트랜잭션 외부에서 Gemini API 호출
            grades = gradingClient.grade(
                    input.facts(),
                    input.evaluation().responseText()
            );
        }

        // 5. 채점 결과를 하나의 트랜잭션으로 저장
        transactionTemplate.executeWithoutResult(status -> {

            Evaluation current =
                    findOwned(userId, evaluationId, true);

            // 다른 요청이 먼저 채점을 완료했다면
            // 기존 결과를 덮어쓰지 않음
            if (current.graded()) {
                return;
            }

            if (!current.submitted()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DELAYED_RECALL_NOT_SUBMITTED",
                        "제출된 평가가 아닙니다."
                );
            }

            // 채점 대상 정보가 변경되지 않았는지 확인
            if (!findFacts(evaluationId).equals(input.facts())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DELAYED_RECALL_FACTS_CHANGED",
                        "채점 대상 정보가 변경되었습니다."
                );
            }

            for (FactGrade grade : grades) {

                int updated = db.update(
                        """
                        UPDATE delayed_recall_facts
                        SET recalled = ?,
                            evidence_quote = ?,
                            grade_note = ?
                        WHERE id = ?
                          AND delayed_recall_id = ?
                        """,
                        grade.recalled(),
                        grade.evidenceQuote(),
                        grade.note(),
                        grade.factId(),
                        evaluationId
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "채점 결과 저장에 실패했습니다."
                    );
                }
            }

            db.update(
                    """
                    UPDATE delayed_recalls
                    SET graded_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(Instant.now()),
                    evaluationId
            );
        });

        // 6. 기존 평가 조회 기능으로 결과 반환
        return delayedRecallService.get(
                userId,
                evaluationId
        );
    }

    private Evaluation findOwned(
            long userId,
            long evaluationId,
            boolean lock
    ) {

        String sql = """
                SELECT d.id,
                       d.response_text,
                       d.cannot_recall,
                       d.submitted_at,
                       d.graded_at
                FROM delayed_recalls d
                JOIN training_sessions s
                    ON s.id = d.source_session_id
                JOIN photos p
                    ON p.id = s.photo_id
                WHERE d.id = ?
                  AND p.user_id = ?
                """;

        if (lock) {
            sql += " FOR UPDATE";
        }

        List<Evaluation> evaluations = db.query(
                sql,
                (rs, rowNum) -> new Evaluation(
                        rs.getLong("id"),
                        rs.getString("response_text"),
                        rs.getBoolean("cannot_recall"),
                        rs.getTimestamp("submitted_at") != null,
                        rs.getTimestamp("graded_at") != null
                ),
                evaluationId,
                userId
        );

        if (evaluations.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DELAYED_RECALL_NOT_FOUND",
                    "회상 평가를 찾을 수 없습니다."
            );
        }

        return evaluations.get(0);
    }

    private List<ReferenceFact> findFacts(
            long evaluationId
    ) {

        return db.query(
                """
                SELECT id, content
                FROM delayed_recall_facts
                WHERE delayed_recall_id = ?
                ORDER BY fact_order
                """,
                (rs, rowNum) -> new ReferenceFact(
                        rs.getLong("id"),
                        rs.getString("content")
                ),
                evaluationId
        );
    }
}
