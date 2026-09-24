package com.konkuk.memory;

import com.konkuk.memory.dto.TokenResponse;
import com.konkuk.memory.dto.UserResponse;
import com.konkuk.memory.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServerSmokeTest {
    @Autowired TestRestTemplate http;

    @Test void actualHttpServerSupportsSignupLoginAndAuthenticatedRequest() {
        var signup = http.postForEntity("/api/auth/signup",
                Map.of("email", "http@example.com", "password", "HttpPassword123!", "name", "HTTP 테스트"), UserResponse.class);
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.getForEntity("/api/users/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var login = http.postForEntity("/api/auth/login",
                Map.of("email", "http@example.com", "password", "HttpPassword123!"), TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        var me = http.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), UserProfileResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().email()).isEqualTo("http@example.com");
    }
}
