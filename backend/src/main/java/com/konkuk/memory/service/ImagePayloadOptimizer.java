package com.konkuk.memory.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

final class ImagePayloadOptimizer {

    static final int MAX_DIMENSION = 1280;
    private static final int RECOMPRESS_THRESHOLD_BYTES = 1_000_000;

    private ImagePayloadOptimizer() {}

    static Payload optimize(byte[] original, String contentType) {
        if (original == null || original.length == 0) {
            return new Payload(original, contentType);
        }

        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                return new Payload(original, contentType);
            }

            int width = source.getWidth();
            int height = source.getHeight();
            int longest = Math.max(width, height);
            boolean shouldResize = longest > MAX_DIMENSION;
            boolean shouldRecompress = original.length > RECOMPRESS_THRESHOLD_BYTES;
            if (!shouldResize && !shouldRecompress) {
                return new Payload(original, contentType);
            }

            double scale = shouldResize ? (double) MAX_DIMENSION / longest : 1.0;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
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
        } catch (Exception ignored) {
            return new Payload(original, contentType);
        }
    }

    record Payload(byte[] bytes, String contentType) {}
}