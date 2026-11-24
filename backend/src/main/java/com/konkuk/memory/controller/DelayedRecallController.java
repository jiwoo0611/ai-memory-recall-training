package com.konkuk.memory.controller;

import com.konkuk.memory.service.DelayedRecallService;
import com.konkuk.memory.service.DelayedRecallService.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class DelayedRecallController {
    private final DelayedRecallService service;
    public DelayedRecallController(DelayedRecallService service) { this.service=service; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/users/me/delayed-recalls/access")
    public Map<String,Boolean> access(@AuthenticationPrincipal Jwt jwt) { return Map.of("canReview",service.canReview(user(jwt))); }
    @GetMapping("/users/me/delayed-recalls")
    public List<View> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) Long albumId) {
        return albumId == null ? service.list(user(jwt)) : service.listForAlbum(user(jwt),albumId);
    }
    @GetMapping("/users/me/delayed-recalls/{id}")
    public View get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return service.get(user(jwt),id); }
    @GetMapping("/users/me/training-sessions/{id}/delayed-recall")
    public View forSession(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return service.forSession(user(jwt),id); }
    @PostMapping("/users/me/training-sessions/{id}/delayed-recall")
    public View setup(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Setup request) { return service.setup(user(jwt),id,request); }
    @PostMapping("/users/me/delayed-recalls/{id}/start")
    public View start(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return service.start(user(jwt),id); }
    @PatchMapping("/users/me/delayed-recalls/{id}/draft")
    public View draft(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Response request) { return service.save(user(jwt),id,request,false); }
    @PostMapping("/users/me/delayed-recalls/{id}/submit")
    public View submit(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Response request) { return service.save(user(jwt),id,request,true); }
    @GetMapping("/review/delayed-recalls")
    public List<View> queue(@AuthenticationPrincipal Jwt jwt) { return service.queue(user(jwt)); }
    @GetMapping("/review/delayed-recalls/{id}")
    public View review(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return service.review(user(jwt),id); }
    @PostMapping("/review/delayed-recalls/{id}/grade")
    public View grade(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Grade request) { return service.grade(user(jwt),id,request); }
}

