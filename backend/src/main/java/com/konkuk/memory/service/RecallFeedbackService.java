package com.konkuk.memory.service;

import com.konkuk.memory.entity.Result;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecallFeedbackService {
    private final TrainingSessionRepository sessions;
    private final AnswerRepository answers;
    private final ResultRepository results;
    private final RecallSummaryGenerator generator;
    private final QuizSummaryGenerator quizGenerator;
    public RecallFeedbackService(TrainingSessionRepository sessions, AnswerRepository answers,
                                 ResultRepository results, RecallSummaryGenerator generator, QuizSummaryGenerator quizGenerator) {
        this.sessions = sessions; this.answers = answers; this.results = results; this.generator = generator;
        this.quizGenerator = quizGenerator;
    }
    // Called only after the answer transaction has committed. A crash rolls this transaction back to PENDING.
    @Transactional
    public void generate(Long userId, Long sessionId) {
        sessions.lockOwned(sessionId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRAINING_SESSION_NOT_FOUND", "훈련 기록을 찾을 수 없습니다."));
        var result = results.findByTrainingSession_IdAndTrainingSession_Photo_User_Id(sessionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ANSWERS_NOT_SAVED", "답변을 먼저 제출해주세요."));
        if (result.getFeedbackStatus() == Result.FeedbackStatus.COMPLETED) return;
        result.startFeedback();
        try {
            var saved = answers.findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(sessionId, userId);
            if (result.getCorrectCount() != null) result.completeQuizFeedback(quizGenerator.generate(result, saved));
            else result.completeFeedback(generator.generate(saved));
        } catch (RuntimeException ex) {
            result.failFeedback();
        }
    }
}
