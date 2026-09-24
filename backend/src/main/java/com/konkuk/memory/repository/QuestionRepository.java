package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByIdAndTrainingSession_IdAndTrainingSession_Photo_User_Id(Long id, Long sessionId, Long userId);
    List<Question> findAllByTrainingSession_IdAndTrainingSession_Photo_User_IdOrderByQuestionOrderAsc(Long sessionId, Long userId);
}
