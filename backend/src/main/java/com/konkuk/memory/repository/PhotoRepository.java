package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Optional<Photo> findByIdAndUser_Id(Long photoId, Long userId);
    Page<Photo> findAllByUser_Id(Long userId, Pageable pageable);
    List<Photo> findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(Long albumId, Long userId);
}
