package com.konkuk.memory.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PhotoConfirmRequest(

        LocalDate takenOn,

        @Size(max = 500, message = "장소는 500자 이하여야 합니다.")
        String location

) {
}