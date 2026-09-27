package com.konkuk.memory.service;

import com.konkuk.memory.dto.PhotoMetadata;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PhotoUploadService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;

    private final ImageValidationService imageValidationService;
    private final PhotoMetadataService photoMetadataService;
    private final FileStorageService fileStorageService;

    public PhotoUploadService(
            AlbumRepository albumRepository,
            UserRepository userRepository,
            PhotoRepository photoRepository,
            ImageValidationService imageValidationService,
            PhotoMetadataService photoMetadataService,
            FileStorageService fileStorageService
    ){
        this.albumRepository = albumRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.imageValidationService = imageValidationService;
        this.photoMetadataService = photoMetadataService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public List<Long> upload(
            Long userId,
            Long albumId,
            List<MultipartFile> files
    ){
        if (files == null || files.isEmpty()){
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILES",
                    "업로드할 사진이 없습니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "USER_FILES",
                        "사용자를 찾을 수 없습니다."
                ));

        Album album = albumRepository.findByIdAndUser_Id(albumId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ALBUM_NOT_FOUND",
                        "앨범을 찾을 수 없습니다."
                ));

        List<String> storedFiles = new ArrayList<>();
        List<Photo> photos = new ArrayList<>();

        try {
            for (MultipartFile file : files){

                imageValidationService.validate(file);

                PhotoMetadata metadata = photoMetadataService.extract(file);

                String extension = getExtension(file.getContentType());

                String storageKey =
                        fileStorageService.store(
                                file,
                                userId,
                                albumId,
                                extension
                        );

                storedFiles.add(storageKey);

                Photo photo = new Photo(
                        user, album, storageKey, file.getOriginalFilename(), file.getContentType(), file.getSize()
                );

                photo.updateMetadate(metadata.takenAt(), metadata.location());

                photos.add(photo);
            }

            List<Photo> savedPhotos = photoRepository.saveAll(photos);
            return savedPhotos.stream().map(Photo::getId).toList();
        } catch (IOException e){

            for (String storageKey : storedFiles){
                fileStorageService.delete(storageKey);
            }

            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_STORAGE_FAILED",
                    "사진 저장에 실패했습니다."
            );
        } catch (RuntimeException e) {
            for (String storageKey : storedFiles){
                fileStorageService.delete(storageKey);
            }
            throw e;
        }
    }

    private void deleteStoredFiles(List<String> storedFiles){

        for (String storageKey : storedFiles){
            fileStorageService.delete(storageKey);
        }
    }

    private String getExtension(String contentType){

        if ("image/png".equals(contentType)){
            return ".png";
        }

        return ".jpg";
    }
}
