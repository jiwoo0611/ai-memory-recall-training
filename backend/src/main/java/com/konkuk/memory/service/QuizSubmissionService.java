package com.konkuk.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.dto.QuizSubmissionRequest;
import com.konkuk.memory.dto.QuizSubmissionResponse;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.entity.Result;
import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.repository.AnswerRepository;
import com.konkuk.memory.repository.ResultRepository;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.QuestionRepository;
import com.konkuk.memory.repository.TrainingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class QuizSubmissionService {

    private final TrainingSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;

    public QuizSubmissionService(
            TrainingSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ObjectMapper objectMapper,
            AnswerRepository answerRepository,
            ResultRepository resultRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public QuizSubmissionResponse submit(
            Long userId,
            Long sessionId,
            QuizSubmissionRequest request
    ) {
        var session = sessionRepository.lockOwned(sessionId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TRAINING_SESSION_NOT_FOUND",
                        "퀴즈 기록을 찾을 수 없습니다."
                ));

        if (session.getStatus() == TrainingSession.Status.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "이미 완료된 훈련입니다. 저장된 결과를 조회해주세요.");
        }
        if (session.getStatus() != TrainingSession.Status.IN_PROGRESS
                || !answerRepository.findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(sessionId, userId).isEmpty()
                || resultRepository.findByTrainingSession_IdAndTrainingSession_Photo_User_Id(sessionId, userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_READY", "답변을 제출할 수 없는 훈련 상태입니다.");
        }

        List<Question> questions = questionRepository
                .findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(
                        sessionId,
                        userId
                );
        if (questions.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUIZ_QUESTIONS_EMPTY",
                    "채점할 객관식 질문이 없습니다."
            );
        }

        Map<Long, Integer> selectedByQuestion = new HashMap<>();
        if (request == null || request.answers() == null) throw invalidAnswers("답안을 입력해주세요.");
        for (QuizSubmissionRequest.AnswerChoice answer : request.answers()) {
            if (answer == null || answer.questionId() == null || answer.selectedOptionIndex() == null) {
                throw invalidAnswers("질문과 선택 답안을 입력해주세요.");
            }
            if (selectedByQuestion.putIfAbsent(answer.questionId(), answer.selectedOptionIndex()) != null) {
                throw invalidAnswers("같은 질문의 답안이 중복되었습니다.");
            }
        }
        if (selectedByQuestion.size() != questions.size()
                || questions.stream().anyMatch(question -> !selectedByQuestion.containsKey(question.getId()))) {
            throw invalidAnswers("모든 질문에 답한 후 제출해주세요.");
        }

        List<QuizSubmissionResponse.QuestionResult> results = questions.stream()
                .map(question -> score(question, selectedByQuestion.get(question.getId())))
                .toList();
        int correctCount = (int) results.stream()
                .filter(QuizSubmissionResponse.QuestionResult::correct)
                .count();

        // Validate every answer before writing. The lock serializes concurrent submissions.
        for (int i = 0; i < questions.size(); i++) {
            var score = results.get(i);
            answerRepository.save(Answer.quiz(questions.get(i), score.selectedOptionIndex(), score.selectedOption(), score.correct()));
        }
        Result result = new Result(session);
        result.recordQuizScore(results.size(), correctCount);
        resultRepository.save(result);
        session.markCompleted();
        answerRepository.flush();

        return new QuizSubmissionResponse(
                sessionId,
                results.size(),
                correctCount,
                results.size() - correctCount,
                results
        );
    }

    private QuizSubmissionResponse.QuestionResult score(Question question, int selectedIndex) {
        if (question.getFormat() != Question.Format.MULTIPLE_CHOICE) throw invalidAnswers("객관식 질문만 제출할 수 있습니다.");
        try {
            List<String> options = objectMapper.readValue(
                    question.getOptionsJson(),
                    new TypeReference<>() {}
            );
            int correctIndex = objectMapper.readTree(question.getAnswerKey())
                    .path("correctOptionIndex")
                    .asInt(-1);
            if (options.size() != 4
                    || selectedIndex < 0
                    || selectedIndex >= options.size()
                    || correctIndex < 0
                    || correctIndex >= options.size()) {
                throw invalidAnswers("객관식 답안 정보를 확인할 수 없습니다.");
            }
            return new QuizSubmissionResponse.QuestionResult(
                    question.getId(),
                    question.getContent(),
                    difficulty(question.getEvidenceSnapshot()),
                    selectedIndex == correctIndex,
                    selectedIndex,
                    options.get(selectedIndex),
                    correctIndex,
                    options.get(correctIndex)
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "QUIZ_SCORING_FAILED",
                    "퀴즈를 채점하지 못했습니다."
            );
        }
    }

    private Question.Difficulty difficulty(String evidenceSnapshot) {
        try {
            String value = objectMapper.readTree(evidenceSnapshot)
                    .path("difficulty")
                    .asText("MEDIUM");
            return Question.Difficulty.valueOf(value);
        } catch (Exception ignored) {
            return Question.Difficulty.MEDIUM;
        }
    }

    private static ApiException invalidAnswers(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUIZ_ANSWERS", message);
    }
}
