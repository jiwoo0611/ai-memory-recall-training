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
import java.util.UUID;

@Service
public class FileStorageService {

    private final S3Client r2Client;
    private final String bucketName;

    public FileStorageService(
            S3Client r2Client,
            @Value("${R2_BUCKET_NAME}") String bucketName
    ) {
        this.r2Client = r2Client;
        this.bucketName = bucketName;
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
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build();

            r2Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );

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
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .build();

            r2Client.deleteObject(request);

        } catch (Exception ignored) {
        }
    }
}
