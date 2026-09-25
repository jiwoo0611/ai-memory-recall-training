package com.konkuk.memory.dto;

import java.time.LocalDateTime;

public record PhotoMetadata (
    LocalDateTime takenAt,
    String location,
    Integer orienation
){
}

