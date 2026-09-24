package com.konkuk.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.memory.repository.RevokedTokenRepository;
import com.konkuk.memory.security.RevokedTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersistentLogoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired RevokedTokenStore store;

    @Test void committedLogoutIsReadByFreshStoreAndRejectsToken() throws Exception {
        assertThat(jdbc.queryForObject("SELECT H2VERSION()", String.class)).isNotBlank();
        String email = "persistent-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "ValidPass123!", "name", "회원"))))
                .andExpect(status().isCreated());
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "ValidPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("accessToken").asText();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getId()).isNotBlank();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        // No test transaction: logout has committed, and a fresh repository/store reads the database.
        RevokedTokenStore freshStore = new RevokedTokenStore(new RevokedTokenRepository(new JdbcTemplate(jdbc.getDataSource())));
        assertThat(freshStore.isRevoked(jwt)).isTrue();
        assertThat(jdbc.queryForObject("SELECT expires_at FROM revoked_tokens WHERE jti = ?",
                java.sql.Timestamp.class, jwt.getId()).toInstant()).isEqualTo(jwt.getExpiresAt());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
    }

    @Test void cleanupDeletesOnlyExpiredRecords() {
        RevokedTokenRepository repository = new RevokedTokenRepository(jdbc);
        String expired = UUID.randomUUID().toString();
        String active = UUID.randomUUID().toString();
        repository.save(expired, Instant.now().minusSeconds(60));
        repository.save(active, Instant.now().plusSeconds(3600));
        store.removeExpired();
        assertThat(repository.exists(expired)).isFalse();
        assertThat(repository.exists(active)).isTrue();
    }

    @Test void duplicateRevocationIsIdempotent() {
        RevokedTokenRepository repository = new RevokedTokenRepository(jdbc);
        String id = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(100);
        repository.save(id, expiry);
        repository.save(id, expiry);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revoked_tokens WHERE jti = ?", Integer.class, id)).isEqualTo(1);
    }
}
