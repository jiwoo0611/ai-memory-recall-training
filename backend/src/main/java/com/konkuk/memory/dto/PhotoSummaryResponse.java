package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Photo;
import java.time.Instant;
import java.time.LocalDate;

public record PhotoSummaryResponse(Long id, String originalFilename, String contentType, long sizeBytes,
                                   Instant createdAt, LocalDate confirmedTakenOn, String confirmedLocation) {
    public static PhotoSummaryResponse from(Photo photo) {
        return new PhotoSummaryResponse(photo.getId(), photo.getOriginalFilename(), photo.getContentType(),
                photo.getSizeBytes(), photo.getCreatedAt(), photo.getConfirmedTakenOn(), photo.getConfirmedLocation());
    }
}
