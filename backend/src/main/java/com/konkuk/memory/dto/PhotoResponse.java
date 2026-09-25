package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Photo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PhotoResponse(
        Long id,
        Long albumId,
        String originalFilename,
        String contentType,
        long sizeBytes,

        LocalDateTime metadataTakenAt,
        String metadataLocation,

        LocalDate confirmedTakenOn,
        String confirmedLocation
) {

    public static PhotoResponse from(Photo photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getAlbum().getId(),
                photo.getOriginalFilename(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getMetadataTakenAt(),
                photo.getMetadataLocation(),
                photo.getConfirmedTakenOn(),
                photo.getConfirmedLocation()
        );
    }
}