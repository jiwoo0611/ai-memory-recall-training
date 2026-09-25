package com.konkuk.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlbumCreateRequest(
        @NotBlank(message = "앨범 이름은 필수입니다.")
        @Size(max= 10, message = "앨범 이름은 10자 이하여야 합니다.")
        String title
) {
}
