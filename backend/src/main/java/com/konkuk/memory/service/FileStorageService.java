package com.konkuk.memory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {

    private final S3Client r2Client;
    private final String bucketName;
    private final boolean local;
    private final java.nio.file.Path root;

    public FileStorageService(
            org.springframework.beans.factory.ObjectProvider<S3Client> r2Client,
            @Value("${R2_BUCKET_NAME:}") String bucketName,
            @Value("${app.storage.mode:r2}") String mode,
            @Value("${app.storage.directory:uploads}") String directory
    ) {
        this.r2Client = r2Client.getIfAvailable();
        this.bucketName = bucketName;
        this.local = "local".equals(mode);
        this.root = java.nio.file.Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(
            MultipartFile file,
            Long userId,
            Long albumId,
            String extension
    ) throws IOException {

        String storageKey =
                userId + "/" +
                albumId + "/" +
                UUID.randomUUID() +
                extension;

        try {
            if (local) {
                var path = localPath(storageKey);
                java.nio.file.Files.createDirectories(path.getParent());
                try (var stream = file.getInputStream()) { java.nio.file.Files.copy(stream, path); }
                return storageKey;
            }
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build();

            try (InputStream inputStream = file.getInputStream()) {
                r2Client.putObject(
                        request,
                        RequestBody.fromInputStream(
                                inputStream,
                                file.getSize()
                        )
                );
            }

            return storageKey;

        } catch (Exception e) {
            throw new IOException(
                    "R2 파일 저장에 실패했습니다.",
                    e
            );
        }
    }

    public byte[] load(String storageKey) throws IOException {

        try {
            if (local) return java.nio.file.Files.readAllBytes(localPath(storageKey));
            GetObjectRequest request =
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .build();

            ResponseBytes<GetObjectResponse> response =
                    r2Client.getObjectAsBytes(request);

            return response.asByteArray();

        } catch (Exception e) {
            throw new IOException(
                    "R2 파일 조회에 실패했습니다.",
                    e
            );
        }
    }

    public void delete(String storageKey) {

        try {
            if (local) { java.nio.file.Files.deleteIfExists(localPath(storageKey)); return; }
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .build();

            r2Client.deleteObject(request);

        } catch (Exception ex) {
            throw new IllegalStateException("사진 파일 삭제를 재시도해야 합니다.", ex);
        }
    }
    private java.nio.file.Path localPath(String key) throws IOException {
        var path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("잘못된 사진 경로입니다.");
        return path;
    }
}
