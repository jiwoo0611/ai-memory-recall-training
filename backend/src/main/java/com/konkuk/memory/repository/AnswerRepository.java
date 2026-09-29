package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Answer;
import com.konkuk.memory.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findAllByQuestion_TrainingSession_IdAndQuestion_TrainingSession_Photo_User_Id(Long sessionId, Long userId);
    List<Answer> findAllByQuestionIn(List<Question> questions);
}
