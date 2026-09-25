package com.konkuk.memory.repository;

import com.konkuk.memory.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    List<Album> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
    Optional<Album> findByIdAndUser_Id(Long albumId, Long userId);
}
