package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoResponse;
import com.konkuk.memory.entity.Photo;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.AlbumRepository;
import com.konkuk.memory.repository.PhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PhotoQueryService {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;

    public PhotoQueryService(
            PhotoRepository photoRepository,
            AlbumRepository albumRepository
    ) {
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
    }

    public List<PhotoResponse> getPhotos(
            Long userId,
            Long albumId
    ) {

        // 현재 사용자가 소유한 앨범인지 확인
        albumRepository
                .findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));

        return photoRepository
                .findAllByAlbum_IdAndUser_IdOrderByCreatedAtAsc(
                        albumId,
                        userId
                )
                .stream()
                .map(PhotoResponse::from)
                .toList();
    }

    public PhotoResponse getPhoto(
            Long userId,
            Long photoId
    ) {

        Photo photo = photoRepository
                .findByIdAndUser_Id(photoId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PHOTO_NOT_FOUND",
                        "사진을 찾을 수 없습니다."
                ));

        return PhotoResponse.from(photo);
    }
}