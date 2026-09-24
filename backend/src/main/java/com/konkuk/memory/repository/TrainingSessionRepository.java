package com.konkuk.memory.repository;

import com.konkuk.memory.entity.TrainingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    @EntityGraph(attributePaths = "photo")
    Optional<TrainingSession> findByIdAndPhoto_User_Id(Long id, Long userId);
    @EntityGraph(attributePaths = "photo")
    Page<TrainingSession> findAllByPhoto_User_Id(Long userId, Pageable pageable);
    Page<TrainingSession> findAllByPhoto_IdAndPhoto_User_Id(Long photoId, Long userId, Pageable pageable);
}
