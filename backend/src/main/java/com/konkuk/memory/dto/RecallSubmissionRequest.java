package com.konkuk.memory.dto;

import com.konkuk.memory.entity.Answer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record RecallSubmissionRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> answers) {
    public record Item(@NotNull @Positive Long questionId, @NotNull Answer.Status status,
                       @Size(max = 2000) String content) {}
}
