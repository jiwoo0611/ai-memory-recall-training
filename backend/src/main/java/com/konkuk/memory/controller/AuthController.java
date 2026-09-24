package com.konkuk.memory.controller;

import com.konkuk.memory.dto.*;
import com.konkuk.memory.service.AuthService;
import com.konkuk.memory.service.UserService;
import com.konkuk.memory.security.RevokedTokenStore;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UserService users;
    private final RevokedTokenStore revokedTokens;
    public AuthController(AuthService auth, UserService users, RevokedTokenStore revokedTokens) {
        this.auth = auth;
        this.users = users;
        this.revokedTokens = revokedTokens;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        users.requireCurrentUser(Long.valueOf(jwt.getSubject()));
        revokedTokens.revoke(jwt);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }
}
