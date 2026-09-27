package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.PhotoRepository;
import com.konkuk.memory.repository.QuestionRepository;
import com.konkuk.memory.repository.TrainingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionGenerationPersistenceService {

    private final PhotoRepository photoRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public QuestionGenerationPersistenceService(
            PhotoRepository photoRepository,
            TrainingSessionRepository trainingSessionRepository,
            QuestionRepository questionRepository,
            ObjectMapper objectMapper
    ) {
        this.photoRepository = photoRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long start(Long userId, Long albumId, Long anchorPhotoId) {
        Photo anchor = photoRepository
                .findByIdAndAlbum_IdAndUser_Id(anchorPhotoId, albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PHOTO_NOT_FOUND",
                        "질문 생성의 기준 사진을 찾을 수 없습니다."
                ));
        TrainingSession session = new TrainingSession(anchor);
        session.markGeneratingQuestions();
        return trainingSessionRepository.saveAndFlush(session).getId();
    }

    @Transactional
    public void complete(
            Long userId,
            Long sessionId,
            String albumEvidenceJson,
            List<GeminiQuestionGenerationClient.GeneratedQuestion> generated,
            String generationVersion
    ) {
        TrainingSession session = ownedSession(userId, sessionId);
        int order = 1;
        for (GeminiQuestionGenerationClient.GeneratedQuestion item : generated) {
            questionRepository.save(new Question(
                    session,
                    order++,
                    item.content(),
                    item.type(),
                    evidenceSnapshot(albumEvidenceJson, item.evidencePhotoIds(), item.displayPhotoIds(), item.difficulty()),
                    item.verifiable(),
                    item.referenceInformation(),
                    generationVersion,
                    Question.Format.MULTIPLE_CHOICE,
                    writeJson(item.options()),
                    answerKey(item.correctOptionIndex())
            ));
        }
        session.markInProgress();
        questionRepository.flush();
    }

    @Transactional
    public void fail(Long userId, Long sessionId) {
        ownedSession(userId, sessionId).markQuestionGenerationFailed();
    }

    private TrainingSession ownedSession(Long userId, Long sessionId) {
        return trainingSessionRepository.findByIdAndPhoto_User_Id(sessionId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TRAINING_SESSION_NOT_FOUND",
                        "훈련 기록을 찾을 수 없습니다."
                ));
    }

    private String evidenceSnapshot(String albumEvidenceJson, List<Long> evidencePhotoIds, List<Long> displayPhotoIds, Question.Difficulty difficulty) {
        try {
            ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.put("scope", "ALBUM");
            snapshot.set("albumEvidence", objectMapper.readTree(albumEvidenceJson));
            snapshot.set("evidencePhotoIds", objectMapper.valueToTree(evidencePhotoIds));
            snapshot.set("displayPhotoIds", objectMapper.valueToTree(displayPhotoIds));
            snapshot.put("difficulty", difficulty.name());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw storageFailure();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw storageFailure();
        }
    }

    private String answerKey(int correctOptionIndex) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("correctOptionIndex", correctOptionIndex);
        return answer.toString();
    }

    private static ApiException storageFailure() {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "QUESTION_EVIDENCE_FAILED",
                "질문 정보를 저장하지 못했습니다."
        );
    }
}
