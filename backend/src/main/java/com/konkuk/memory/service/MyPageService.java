package com.konkuk.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.dto.*;
import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MyPageService {
    private final UserService users;
    private final PhotoRepository photos;
    private final TrainingSessionRepository sessions;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final ResultRepository results;
    private final ObjectMapper objectMapper;

    public MyPageService(UserService users, PhotoRepository photos, TrainingSessionRepository sessions,
                         QuestionRepository questions, AnswerRepository answers, ResultRepository results,
                         ObjectMapper objectMapper) {
        this.users = users;
        this.photos = photos;
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.results = results;
        this.objectMapper = objectMapper;
    }

    public PageResponse<PhotoSummaryResponse> photos(Long currentUserId, int page, int size) {
        var pageable = pageRequest(page, size, "createdAt");
        users.requireCurrentUser(currentUserId);
        return PageResponse.from(photos.findAllByUser_Id(currentUserId, pageable).map(PhotoSummaryResponse::from));
    }

    public PageResponse<TrainingSessionSummaryResponse> trainingSessions(Long currentUserId, int page, int size) {
        var pageable = pageRequest(page, size, "startedAt");
        users.requireCurrentUser(currentUserId);
        return PageResponse.from(sessions.findAllByPhoto_User_Id(currentUserId, pageable)
                .map(TrainingSessionSummaryResponse::from));
    }

    public TrainingSessionDetailResponse trainingSession(Long currentUserId, Long sessionId) {
        if (sessionId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SESSION_ID", "훈련 ID는 양수여야 합니다.");
        }
        users.requireCurrentUser(currentUserId);
        var session = sessions.findByIdAndPhoto_User_Id(sessionId, currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRAINING_SESSION_NOT_FOUND", "훈련 기록을 찾을 수 없습니다."));
        var ownedQuestions = questions.findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(sessionId, currentUserId);
        Map<Long, Answer> byQuestion = answers
                .findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(sessionId, currentUserId)
                .stream().collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
        var questionResponses = ownedQuestions.stream()
                .map(question -> TrainingSessionDetailResponse.QuestionInfo.from(
                        question,
                        byQuestion.get(question.getId()),
                        objectMapper
                ))
                .toList();
        var resultResponse = results.findByTrainingSession_IdAndTrainingSession_Photo_User_Id(sessionId, currentUserId)
                .map(TrainingSessionDetailResponse.ResultInfo::from).orElse(null);
        return new TrainingSessionDetailResponse(session.getId(), TrainingSessionSummaryResponse.PhotoInfo.from(session.getPhoto()),
                session.getStatus(), session.getStartedAt(), session.getCompletedAt(), questionResponses, resultResponse);
    }

    private PageRequest pageRequest(int page, int size, String sortField) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page는 0 이상, size는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortField, "id"));
    }
}
