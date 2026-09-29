package com.konkuk.memory.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePayloadOptimizerTest {

    @Test
    void calculatesSubsamplingBeforeDecodingVeryLargeImages() {
        assertThat(ImagePayloadOptimizer.sourceSubsampling(12_000, 9_000)).isEqualTo(9);
        assertThat(ImagePayloadOptimizer.sourceSubsampling(1_280, 960)).isEqualTo(1);
    }

    @Test
    void shrinksLargePhotoBeforeGeminiUpload() throws Exception {
        BufferedImage source = new BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int red = (x * 37 + y * 13) & 255;
                int green = (x * 17 + y * 29) & 255;
                int blue = (x * 7 + y * 43) & 255;
                source.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(source, "png", original);

        ImagePayloadOptimizer.Payload optimized =
                ImagePayloadOptimizer.optimize(original.toByteArray(), "image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(optimized.bytes()));

        assertThat(optimized.contentType()).isEqualTo("image/jpeg");
        assertThat(optimized.bytes().length).isLessThan(original.size());
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .isLessThanOrEqualTo(ImagePayloadOptimizer.MAX_DIMENSION);
    }

    @Test
    void keepsUnknownImagePayloadUntouched() {
        byte[] bytes = new byte[]{1, 2, 3, 4};

        ImagePayloadOptimizer.Payload optimized =
                ImagePayloadOptimizer.optimize(bytes, "image/heic");

        assertThat(optimized.bytes()).isSameAs(bytes);
        assertThat(optimized.contentType()).isEqualTo("image/heic");
    }
}
