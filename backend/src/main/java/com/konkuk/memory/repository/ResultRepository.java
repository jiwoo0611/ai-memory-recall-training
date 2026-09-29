package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Result;
import com.konkuk.memory.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {
    Optional<Result> findByTrainingSession_IdAndTrainingSession_Photo_User_Id(Long sessionId, Long userId);
    List<Result> findAllByTrainingSessionIn(List<TrainingSession> trainingSessions);
}
