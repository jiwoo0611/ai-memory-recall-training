package com.konkuk.memory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuizSubmissionRequest(
        @NotEmpty(message = "답안을 입력해주세요.")
        @Size(max = 12, message = "답안은 최대 12개까지 제출할 수 있습니다.")
        List<@NotNull @Valid AnswerChoice> answers
) {
    public record AnswerChoice(
            @NotNull @Positive Long questionId,
            @NotNull @Min(0) @Max(3) Integer selectedOptionIndex
    ) {}
}
