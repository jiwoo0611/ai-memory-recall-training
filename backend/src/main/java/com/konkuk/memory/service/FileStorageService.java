package com.konkuk.memory.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation =
            Paths.get("uploads").toAbsolutePath().normalize();

    public String store(
            byte[] imageBytes,
            Long userId,
            Long albumId,
            String extension
    ) throws IOException {

        String storedFilename =
                UUID.randomUUID() + extension;

        Path directory = rootLocation
                .resolve(String.valueOf(userId))
                .resolve(String.valueOf(albumId));

        Files.createDirectories(directory);

        Path destination = directory
                .resolve(storedFilename)
                .normalize();

        if (!destination.startsWith(rootLocation)) {
            throw new IOException("잘못된 파일 저장 경로입니다.");
        }

        Files.write(destination, imageBytes);

        return rootLocation
                .relativize(destination)
                .toString();
    }

    public byte[] load(String storageKey) throws IOException {

        Path path = rootLocation
                .resolve(storageKey)
                .normalize();

        if (!path.startsWith(rootLocation)) {
            throw new IOException("잘못된 파일 경로입니다.");
        }

        if (!Files.exists(path)) {
            throw new IOException("사진 파일을 찾을 수 없습니다.");
        }

        return Files.readAllBytes(path);
    }

    public void delete(String storageKey) {

        try {
            Path path = rootLocation
                    .resolve(storageKey)
                    .normalize();

            if (path.startsWith(rootLocation)) {
                Files.deleteIfExists(path);
            }

        } catch (IOException ignored) {
        }
    }
}
