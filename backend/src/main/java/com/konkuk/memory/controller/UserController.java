package com.konkuk.memory.controller;

import com.konkuk.memory.dto.*;
import com.konkuk.memory.service.MyPageService;
import com.konkuk.memory.service.QuizSubmissionService;
import com.konkuk.memory.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    private final MyPageService myPage;
    private final QuizSubmissionService quizSubmission;
    public UserController(UserService users, MyPageService myPage, QuizSubmissionService quizSubmission) {
        this.users = users;
        this.myPage = myPage;
        this.quizSubmission = quizSubmission;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.currentUser(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/me/photos")
    public PageResponse<PhotoSummaryResponse> photos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return myPage.photos(Long.valueOf(jwt.getSubject()), page, size);
    }

    @GetMapping("/me/training-sessions")
    public PageResponse<TrainingSessionSummaryResponse> trainingSessions(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return myPage.trainingSessions(Long.valueOf(jwt.getSubject()), page, size);
    }

    @GetMapping("/me/training-sessions/{sessionId}")
    public TrainingSessionDetailResponse trainingSession(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        return myPage.trainingSession(Long.valueOf(jwt.getSubject()), sessionId);
    }
    @PostMapping("/me/training-sessions/{sessionId}/submit")
    public QuizSubmissionResponse submitQuiz(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sessionId,
            @Valid @RequestBody QuizSubmissionRequest request
    ) {
        return quizSubmission.submit(Long.valueOf(jwt.getSubject()), sessionId, request);
    }
}
