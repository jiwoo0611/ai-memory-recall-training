package com.konkuk.memory.config;

import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Explicit local profile only. Creates a demo account once; never replaces saved answers. */
@Configuration
@Profile("local")
public class LocalDemoConfig {
    @Bean
    ApplicationRunner localDemo(UserRepository users, AlbumRepository albums, PhotoRepository photos,
                                TrainingSessionRepository sessions, QuestionRepository questions,
                                PasswordEncoder encoder, PlatformTransactionManager manager) {
        return args -> {
            if (users.findByEmail("demo@example.com").isPresent()) return;
            var path = Path.of("uploads", "local-demo", "park.png");
            Files.createDirectories(path.getParent());
            var image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(new Color(220, 236, 239)); graphics.fillRect(0, 0, 1000, 600);
            graphics.setColor(new Color(176, 204, 167)); graphics.fillRect(0, 300, 1000, 300);
            graphics.setColor(new Color(236, 221, 190)); graphics.fillPolygon(new int[]{440,560,820,180}, new int[]{300,300,600,600}, 4);
            graphics.setColor(new Color(93, 125, 91));
            graphics.fillOval(100,150,230,230); graphics.fillOval(700,110,220,220);
            graphics.setColor(new Color(106, 79, 61)); graphics.fillRect(205,310,25,110); graphics.fillRect(800,270,25,130);
            graphics.setColor(new Color(241, 193, 115)); graphics.fillOval(450,80,85,85);
            graphics.dispose(); ImageIO.write(image, "png", path.toFile());
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                var user = users.save(new User("demo@example.com", encoder.encode("DemoPass123!"), "로컬 체험 사용자"));
                var album = albums.save(new Album(user, "로컬 체험 · 공원 산책"));
                var photo = photos.save(new Photo(user, album, "local-demo/park.png", "공원-체험용-그림.png", "image/png", 10000));
                var session = new TrainingSession(photo); session.markInProgress(); sessions.save(session);
                String[] prompts = {"이런 공원에서 누구와 시간을 보낸 기억이 있나요?", "산책하며 나눴던 이야기나 떠오르는 장면이 있나요?", "그날의 기분이나 기억에 남는 소리를 들려주세요."};
                for (int i = 0; i < prompts.length; i++) questions.save(new Question(session, i + 1, prompts[i],
                        Question.Type.EXPERIENCE, "{}", false, null, "local-demo-v1"));
            });
        };
    }
}
