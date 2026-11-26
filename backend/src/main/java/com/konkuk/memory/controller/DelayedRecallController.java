
package com.konkuk.memory.controller;

import com.konkuk.memory.service.DelayedRecallService;
import com.konkuk.memory.service.DelayedRecallService.*;
import com.konkuk.memory.service.DelayedRecallGradingService;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class DelayedRecallController {

    private final DelayedRecallService service;
    private final DelayedRecallGradingService gradingService;

    public DelayedRecallController(
            DelayedRecallService service,
            DelayedRecallGradingService gradingService
    ) {
        this.service = service;
        this.gradingService = gradingService;
    }

    private long user(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    @GetMapping("/users/me/delayed-recalls")
    public List<View> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long albumId
    ) {
        return albumId == null
                ? service.list(user(jwt))
                : service.listForAlbum(user(jwt), albumId);
    }

    @GetMapping("/users/me/delayed-recalls/{id:[0-9]+}")
    public View get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id
    ) {
        return service.get(user(jwt), id);
    }

    @GetMapping("/users/me/training-sessions/{id}/delayed-recall")
    public View forSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id
    ) {
        return service.forSession(user(jwt), id);
    }

    @PostMapping("/users/me/training-sessions/{id}/delayed-recall")
    public View setup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody Setup request
    ) {
        return service.setup(user(jwt), id, request);
    }

    @PostMapping("/users/me/delayed-recalls/{id}/start")
    public View start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id
    ) {
        return service.start(user(jwt), id);
    }

    @PatchMapping("/users/me/delayed-recalls/{id}/draft")
    public View draft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody Response request
    ) {
        return service.save(user(jwt), id, request, false);
    }

    // 평가 제출 및 자동 AI 채점
    @PostMapping("/users/me/delayed-recalls/{id}/submit")
    public View submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody Response request
    ) {
        long userId = user(jwt);

        // 1. 사용자 응답을 먼저 저장
        service.save(userId, id, request, true);

        // 2. 저장 완료 후 AI 채점 실행
        return gradingService.grade(userId, id);
    }

    // AI 채점 실패 시 재시도
    @PostMapping("/users/me/delayed-recalls/{id}/grade")
    public View grade(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id
    ) {
        return gradingService.grade(user(jwt), id);
    }
}
