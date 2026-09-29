package com.konkuk.memory.service;

import com.konkuk.memory.dto.AlbumQuizResetResponse;
import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.entity.Question;
import com.konkuk.memory.entity.Result;
import com.konkuk.memory.entity.TrainingSession;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.AnswerRepository;
import com.konkuk.memory.repository.QuestionRepository;
import com.konkuk.memory.repository.ResultRepository;
import com.konkuk.memory.repository.TrainingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlbumQuizResetService {

    private final AlbumRepository albumRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;

    public AlbumQuizResetService(
            AlbumRepository albumRepository,
            TrainingSessionRepository trainingSessionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository
    ) {
        this.albumRepository = albumRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public AlbumQuizResetResponse reset(Long userId, Long albumId) {
        albumRepository.findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));

        List<TrainingSession> sessions = trainingSessionRepository
                .findAllByPhoto_Album_IdAndPhoto_User_Id(albumId, userId);

        if (sessions.isEmpty()) {
            return new AlbumQuizResetResponse(0, 0, 0, 0);
        }

        List<Question> questions = questionRepository.findAllByTrainingSessionIn(sessions);
        List<Answer> answers = questions.isEmpty()
                ? List.of()
                : answerRepository.findAllByQuestionIn(questions);
        List<Result> results = resultRepository.findAllByTrainingSessionIn(sessions);

        if (!answers.isEmpty()) {
            answerRepository.deleteAllInBatch(answers);
        }
        if (!results.isEmpty()) {
            resultRepository.deleteAllInBatch(results);
        }
        if (!questions.isEmpty()) {
            questionRepository.deleteAllInBatch(questions);
        }
        trainingSessionRepository.deleteAllInBatch(sessions);

        return new AlbumQuizResetResponse(
                sessions.size(),
                questions.size(),
                answers.size(),
                results.size()
        );
    }
}
