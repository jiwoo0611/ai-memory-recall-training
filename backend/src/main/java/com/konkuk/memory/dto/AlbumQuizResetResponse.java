package com.konkuk.memory.dto;

public record AlbumQuizResetResponse(
        int deletedSessionCount,
        int deletedQuestionCount,
        int deletedAnswerCount,
        int deletedResultCount
) {}
