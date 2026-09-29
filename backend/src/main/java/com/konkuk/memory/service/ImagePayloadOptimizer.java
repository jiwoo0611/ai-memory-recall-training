package com.konkuk.memory.service;

import com.konkuk.memory.exception.ApiException;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

final class ImagePayloadOptimizer {

    static final int MAX_DIMENSION = 1280;
    private static final int RECOMPRESS_THRESHOLD_BYTES = 1_000_000;

    private ImagePayloadOptimizer() {}

    static Payload optimize(byte[] original, String contentType) {
        if (original == null || original.length == 0) {
            return new Payload(original, contentType);
        }

        BufferedImage source = null;
        BufferedImage target = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (input == null) {
                return new Payload(original, contentType);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return new Payload(original, contentType);
            }

            ImageReader reader = readers.next();
            int width;
            int height;
            try {
                reader.setInput(input, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);

                int longest = Math.max(width, height);
                boolean shouldResize = longest > MAX_DIMENSION;
                boolean shouldRecompress = original.length > RECOMPRESS_THRESHOLD_BYTES;
                if (!shouldResize && !shouldRecompress) {
                    return new Payload(original, contentType);
                }

                ImageReadParam readParam = reader.getDefaultReadParam();
                int subsampling = sourceSubsampling(width, height);
                if (subsampling > 1) {
                    readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                source = reader.read(0, readParam);
            } finally {
                reader.dispose();
            }

            if (source == null) {
                return new Payload(original, contentType);
            }

            width = source.getWidth();
            height = source.getHeight();
            int longest = Math.max(width, height);
            boolean shouldResize = longest > MAX_DIMENSION;

            double scale = shouldResize ? (double) MAX_DIMENSION / longest : 1.0;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(target, "jpeg", output)) {
                return new Payload(original, contentType);
            }
            byte[] optimized = output.toByteArray();
            return optimized.length < original.length
                    ? new Payload(optimized, "image/jpeg")
                    : new Payload(original, contentType);
        } catch (OutOfMemoryError error) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "IMAGE_ANALYSIS_TOO_LARGE",
                    "이미지 해상도가 너무 커서 분석하지 못했습니다. 더 작은 이미지로 다시 업로드해주세요."
            );
        } catch (Exception ignored) {
            return new Payload(original, contentType);
        } finally {
            if (target != null) {
                target.flush();
            }
            if (source != null && source != target) {
                source.flush();
            }
        }
    }

    static int sourceSubsampling(int width, int height) {
        int longest = Math.max(width, height);
        return longest > MAX_DIMENSION ? Math.max(1, longest / MAX_DIMENSION) : 1;
    }

    record Payload(byte[] bytes, String contentType) {}
}
