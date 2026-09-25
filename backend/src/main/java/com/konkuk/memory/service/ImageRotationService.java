package com.konkuk.memory.service;

import com.konkuk.memory.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageRotationService {

    public byte[] correctRotation(
            MultipartFile file,
            Integer orientation
    ) {

        try {

            byte[] originalBytes = file.getBytes();

            // Orientation 정보가 없거나 정상 방향이면 원본 그대로 사용
            if (orientation == null || orientation == 1) {
                return originalBytes;
            }

            BufferedImage originalImage = ImageIO.read(
                    new ByteArrayInputStream(originalBytes)
            );

            if (originalImage == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CORRUPTED_IMAGE",
                        "이미지를 읽을 수 없습니다."
                );
            }

            BufferedImage rotatedImage =
                    rotate(originalImage, orientation);

            String format = getFormat(file.getContentType());

            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            boolean written = ImageIO.write(
                    rotatedImage,
                    format,
                    outputStream
            );

            if (!written) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "IMAGE_ROTATION_FAILED",
                        "회전된 이미지를 저장할 수 없습니다."
                );
            }

            return outputStream.toByteArray();

        } catch (IOException e) {

            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "IMAGE_ROTATION_FAILED",
                    "사진 회전 보정에 실패했습니다."
            );
        }
    }

    private BufferedImage rotate(
            BufferedImage image,
            int orientation
    ) {

        int width = image.getWidth();
        int height = image.getHeight();

        AffineTransform transform =
                new AffineTransform();

        int newWidth = width;
        int newHeight = height;

        switch (orientation) {

            case 3 -> {
                // 180도 회전
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }

            case 6 -> {
                // 시계 방향 90도 회전
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);

                newWidth = height;
                newHeight = width;
            }

            case 8 -> {
                // 반시계 방향 90도 회전
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);

                newWidth = height;
                newHeight = width;
            }

            default -> {
                // 우리가 처리하지 않는 Orientation이면
                // 이미지 자체는 그대로 반환
                return image;
            }
        }

        BufferedImage rotatedImage =
                new BufferedImage(
                        newWidth,
                        newHeight,
                        getImageType(image)
                );

        Graphics2D graphics =
                rotatedImage.createGraphics();

        try {
            graphics.drawImage(
                    image,
                    transform,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return rotatedImage;
    }

    private int getImageType(BufferedImage image) {

        if (image.getType() == BufferedImage.TYPE_CUSTOM) {
            return BufferedImage.TYPE_INT_RGB;
        }

        return image.getType();
    }

    private String getFormat(String contentType) {

        if ("image/png".equals(contentType)) {
            return "png";
        }

        return "jpg";
    }
}