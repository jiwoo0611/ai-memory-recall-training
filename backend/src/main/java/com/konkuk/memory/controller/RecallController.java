package com.konkuk.memory.controller;

import com.konkuk.memory.dto.*;
import com.konkuk.memory.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/training-sessions")
public class RecallController {
    private final RecallService recall;
    private final RecallFeedbackService feedback;
    private final MyPageService history;
    public RecallController(RecallService recall, RecallFeedbackService feedback, MyPageService history) {
        this.recall = recall; this.feedback = feedback; this.history = history;
    }
    @PostMapping("/{id}/answers")
    public TrainingSessionDetailResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                @Valid @RequestBody RecallSubmissionRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        recall.submit(userId, id, request);
        return history.trainingSession(userId, id);
    }
    @PostMapping("/{id}/feedback")
    public TrainingSessionDetailResponse feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long userId = Long.valueOf(jwt.getSubject());
        feedback.generate(userId, id);
        return history.trainingSession(userId, id);
    }
    public record RetryRequest(@NotNull UUID requestId) {}
    @PostMapping("/{id}/retrain")
    public Map<String, Long> retrain(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody RetryRequest request) {
        return Map.of("sessionId", recall.retrain(Long.valueOf(jwt.getSubject()), id, request.requestId().toString()));
    }
}
