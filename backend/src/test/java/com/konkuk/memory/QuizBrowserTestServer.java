package com.konkuk.memory;

import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in browser fixture. Isolated in-memory DB; never connects to the user's DB. */
public class QuizBrowserTestServer {
    public static void main(String[] args) {
        var app = new SpringApplication(MemoryRecallApplication.class);
        var context = app.run("--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=18081",
                "--spring.datasource.url=jdbc:h2:mem:quizbrowser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "--app.cors.allowed-origins=http://localhost:5174");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
            var user = context.getBean(UserRepository.class).save(new User("quiz-browser@example.com",
                    context.getBean(PasswordEncoder.class).encode("BrowserTest123!"), "브라우저 테스트"));
            var album = context.getBean(AlbumRepository.class).save(new Album(user, "테스트 앨범"));
            var photo = context.getBean(PhotoRepository.class).save(new Photo(user, album, "quiz-browser-fixture",
                    "테스트 사진.jpg", "image/jpeg", 10));
            var session = new TrainingSession(photo); session.markInProgress();
            context.getBean(TrainingSessionRepository.class).save(session);
            for (int i = 1; i <= 3; i++) context.getBean(QuestionRepository.class).save(new Question(session, i,
                    "테스트 질문 " + i, Question.Type.EXPERIENCE, "{}", false, null, "browser-fixture",
                    Question.Format.MULTIPLE_CHOICE, "[\"보기 A\",\"보기 B\",\"보기 C\",\"보기 D\"]", "{\"correctOptionIndex\":0}"));
        });
    }
}
