package com.konkuk.memory.controller;

import com.konkuk.memory.dto.AlbumCreateRequest;
import com.konkuk.memory.dto.AlbumResponse;
import com.konkuk.memory.service.AlbumService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService){
        this.albumService = albumService;
    }

    @PostMapping
    public AlbumResponse createAlbum(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AlbumCreateRequest request
            ){

        Long userId = Long.valueOf(jwt.getSubject());

        return albumService.create(userId, request);
    }

    @GetMapping
    public List<AlbumResponse> getAlbums(
            @AuthenticationPrincipal Jwt jwt
    ){
        Long userId = Long.valueOf((jwt.getSubject()));

        return albumService.getAlbums(userId);
    }

    @GetMapping("/{albumId}")
    public AlbumResponse getAlbum(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long albumId
    ){
        Long userId = Long.valueOf(jwt.getSubject());

        return albumService.getAlbum(userId, albumId);
    }
}
