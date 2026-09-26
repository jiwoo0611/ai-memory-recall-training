package com.konkuk.memory.service;

import com.konkuk.memory.dto.AlbumCreateRequest;
import com.konkuk.memory.dto.AlbumResponse;
import com.konkuk.memory.entity.Album;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.entity.User;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import com.konkuk.memory.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final FileStorageService fileStorageService;

    public AlbumService(
            AlbumRepository albumRepository,
            UserRepository userRepository,
            PhotoRepository photoRepository,
            FileStorageService fileStorageService
    ) {
        this.albumRepository = albumRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public AlbumResponse create(
            Long userId,
            AlbumCreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."
                ));

        String title = request.title().trim();

        Album album = new Album(user, title);

        Album savedAlbum = albumRepository.save(album);

        return AlbumResponse.from(savedAlbum);
    }

    public List<AlbumResponse> getAlbums(Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "사용자를 찾을 수 없습니다."
            );
        }

        return albumRepository
                .findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(AlbumResponse::from)
                .toList();
    }

    public AlbumResponse getAlbum(
            Long userId,
            Long albumId
    ) {
        Album album = albumRepository
                .findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));

        return AlbumResponse.from(album);
    }

    @Transactional
    public void deleteAlbum(
            Long userId,
            Long albumId
    ) {
        Album album = albumRepository
                .findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));

        List<Photo> photos =
                photoRepository
                        .findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(
                                albumId,
                                userId
                        );

        for (Photo photo : photos) {
            fileStorageService.delete(
                    photo.getStorageKey()
            );
        }

        photoRepository.deleteAll(photos);
        photoRepository.flush();

        albumRepository.delete(album);
        albumRepository.flush();
    }
}
