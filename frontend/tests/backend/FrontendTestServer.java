package com.konkuk.memory;

import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Browser-test fixture launcher. Only compiled by tests/backend.init.gradle. */
public class FrontendTestServer {
    public static void main(String[] args) {
        if (!"org.h2.Driver".equals(System.getProperty("spring.datasource.driver-class-name"))
                || !System.getProperty("spring.datasource.url", "").startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException("Browser tests require an isolated in-memory H2 database");
        }
        var context = SpringApplication.run(MemoryRecallApplication.class, args);
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.queryForObject("SELECT H2VERSION()", String.class);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
            var owner = context.getBean(UserRepository.class).save(new User("demo@example.com",
                    context.getBean(PasswordEncoder.class).encode("DemoPass123!"), "시연 사용자"));
            for (int i = 1; i <= 8; i++) {
                var photo = context.getBean(PhotoRepository.class).save(new Photo(owner, "test-only/" + i,
                        "산책-" + i + ".jpg", "image/jpeg", 150000));
                var session = context.getBean(TrainingSessionRepository.class).save(new TrainingSession(photo));
                if (i == 8) {
                    var question = context.getBean(QuestionRepository.class).save(new Question(session, 1,
                            "누구와 함께한 산책인가요?", Question.Type.EXPERIENCE, "[]", false, null, "fixture"));
                    context.getBean(AnswerRepository.class).save(new Answer(question, Answer.Status.ANSWERED, "가족과 함께 공원을 걸었습니다."));
                    var result = context.getBean(ResultRepository.class).saveAndFlush(new Result(session));
                    jdbc.update("UPDATE results SET feedback_status='COMPLETED', summary=?, answered_count=1 WHERE id=?",
                            "가족과의 즐거운 시간을 회상했습니다.", result.getId());
                }
            }
        });
    }
}
