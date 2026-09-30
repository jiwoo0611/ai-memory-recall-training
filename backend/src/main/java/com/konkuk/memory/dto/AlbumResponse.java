package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Album;

import java.time.Instant;

public record AlbumResponse(
        Long id,
        String title,
        Instant createdAt,
        long photoCount
) {
    public static AlbumResponse from(Album album, long photoCount){
        return new AlbumResponse(
                album.getId(),
                album.getTitle(),
                album.getCreatedAt(),
                photoCount
        );
    }
}
