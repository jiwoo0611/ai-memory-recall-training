package com.konkuk.memory.service;

import com.konkuk.memory.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
public class ImageValidationService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    public void validate(MultipartFile file) {

        validateNotEmpty(file);
        validateSize(file);
        validateContentType(file);
        validateMagicBytes(file);
        validateImageCanBeDecoded(file);
    }

    private void validateNotEmpty(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILE",
                    "빈 파일은 업로드할 수 없습니다."
            );
        }
    }

    private void validateSize(MultipartFile file) {

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILE_TOO_LARGE",
                    "사진은 최대 10MB까지 업로드할 수 있습니다."
            );
        }
    }

    private void validateContentType(MultipartFile file) {

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType)) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_FILE_TYPE",
                    "JPEG와 PNG 이미지만 업로드할 수 있습니다."
            );
        }
    }

    private void validateMagicBytes(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {

            byte[] header = inputStream.readNBytes(8);

            boolean jpeg = isJpeg(header);
            boolean png = isPng(header);

            if (!jpeg && !png) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_IMAGE_FORMAT",
                        "실제 이미지 형식이 JPEG 또는 PNG가 아닙니다."
                );
            }

        } catch (IOException e) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IMAGE_READ_FAILED",
                    "이미지 파일을 읽을 수 없습니다."
            );
        }
    }

    private boolean isJpeg(byte[] header) {

        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] header) {

        return header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && (header[1] & 0xFF) == 0x50
                && (header[2] & 0xFF) == 0x4E
                && (header[3] & 0xFF) == 0x47
                && (header[4] & 0xFF) == 0x0D
                && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A
                && (header[7] & 0xFF) == 0x0A;
    }

    private void validateImageCanBeDecoded(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {

            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CORRUPTED_IMAGE",
                        "손상되었거나 읽을 수 없는 이미지입니다."
                );
            }

        } catch (IOException e) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CORRUPTED_IMAGE",
                    "손상되었거나 읽을 수 없는 이미지입니다."
            );
        }
    }
}