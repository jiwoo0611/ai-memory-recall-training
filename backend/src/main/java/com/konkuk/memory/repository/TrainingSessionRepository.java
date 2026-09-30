package com.konkuk.memory.repository;

import com.konkuk.memory.entity.TrainingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.id = :id and s.photo.user.id = :userId")
    Optional<TrainingSession> lockOwned(@org.springframework.data.repository.query.Param("id") Long id,
                                      @org.springframework.data.repository.query.Param("userId") Long userId);
    @org.springframework.data.jpa.repository.Query(value = "SELECT s.* FROM training_sessions s JOIN training_retries r ON r.target_session_id=s.id WHERE r.source_session_id=:sourceSessionId AND r.request_id=:retryKey", nativeQuery = true)
    Optional<TrainingSession> findBySourceSessionIdAndRetryKey(
            @org.springframework.data.repository.query.Param("sourceSessionId") Long sourceSessionId,
            @org.springframework.data.repository.query.Param("retryKey") String retryKey);
    @EntityGraph(attributePaths = "photo")
    Optional<TrainingSession> findByIdAndPhoto_User_Id(Long id, Long userId);
    @EntityGraph(attributePaths = "photo")
    Page<TrainingSession> findAllByPhoto_User_Id(Long userId, Pageable pageable);
    Page<TrainingSession> findAllByPhoto_IdAndPhoto_User_Id(Long photoId, Long userId, Pageable pageable);
    List<TrainingSession> findAllByPhoto_Album_IdAndPhoto_User_Id(Long albumId, Long userId);
}
