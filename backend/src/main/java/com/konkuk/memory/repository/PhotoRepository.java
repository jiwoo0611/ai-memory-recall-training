package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Optional<Photo> findByIdAndUser_Id(Long id, Long userId);
    Page<Photo> findAllByUser_Id(Long userId, Pageable pageable);
}
