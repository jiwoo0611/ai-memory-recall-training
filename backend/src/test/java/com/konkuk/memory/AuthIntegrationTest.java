package com.konkuk.memory;

import com.fasterxml.jackson.databind.*;
import com.konkuk.memory.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;

    private ResultActions signup(String email, String password, String name) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password, "name", name))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String loginToken(String email) throws Exception {
        return mapper.readTree(login(email, "ValidPass123!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test void signupHashesPasswordAndNeverReturnsIt() throws Exception {
        signup("  Person@Example.com  ", "ValidPass123!", " 홍길동 ")
                .andExpect(status().isCreated()).andExpect(jsonPath("email").value("person@example.com"))
                .andExpect(jsonPath("name").value("홍길동"))
                .andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist());
        var user = users.findByEmail("person@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("ValidPass123!").startsWith("$2");
        assertThat(passwords.matches("ValidPass123!", user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test void duplicateEmailIsConflictIncludingCaseDifferences() throws Exception {
        signup("duplicate@example.com", "ValidPass123!", "회원").andExpect(status().isCreated());
        signup("DUPLICATE@example.com", "ValidPass123!", "회원").andExpect(status().isConflict());
    }

    @Test void invalidAndMissingFieldsAreBadRequest() throws Exception {
        signup("invalid", "short", " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("fieldErrors.email").exists())
                .andExpect(jsonPath("fieldErrors.password").exists())
                .andExpect(jsonPath("fieldErrors.name").exists());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void bcryptByteLimitIsValidatedBeforeEncoding() throws Exception {
        signup("long@example.com", "가".repeat(25), "회원")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PASSWORD_TOO_LONG"));
        login("long@example.com", "가".repeat(25)).andExpect(status().isBadRequest());
    }

    @Test void malformedJsonAndUnexpectedFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_JSON"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"ValidPass123!\",\"name\":\"A\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void loginReturnsUsableBearerToken() throws Exception {
        signup("login@example.com", "ValidPass123!", "회원");
        login("LOGIN@example.com", "ValidPass123!").andExpect(status().isOk())
                .andExpect(jsonPath("tokenType").value("Bearer"))
                .andExpect(jsonPath("expiresIn").value(1800))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + loginToken("login@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value("login@example.com"))
                .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    @Test void wrongPasswordAndUnknownUserHaveSameError() throws Exception {
        signup("known@example.com", "ValidPass123!", "회원");
        login("known@example.com", "Incorrect123!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        login("unknown@example.com", "Incorrect123!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
    }

    @Test void missingMalformedAndTamperedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        signup("tamper@example.com", "ValidPass123!", "회원");
        String[] parts = loginToken("tamper@example.com").split("\\.");
        String altered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + altered))
                .andExpect(status().isUnauthorized());
    }

    private String signedToken(String subject, String issuer, String audience, Instant expiry) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().subject(subject).issuer(issuer)
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(3600));
        if (expiry != null) builder.expiresAt(expiry);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    @Test void expiredWrongIssuerWrongAudienceAndMissingExpiryAreRejected() throws Exception {
        for (String token : List.of(
                signedToken("1", "memory-recall-test", "memory-recall-api", Instant.now().minusSeconds(10)),
                signedToken("1", "wrong-issuer", "memory-recall-api", Instant.now().plusSeconds(100)),
                signedToken("1", "memory-recall-test", "wrong-audience", Instant.now().plusSeconds(100)),
                signedToken("1", "memory-recall-test", "memory-recall-api", null),
                signedToken("not-an-id", "memory-recall-test", "memory-recall-api", Instant.now().plusSeconds(100)))) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void tokenForDeletedUserIsRejected() throws Exception {
        signup("deleted@example.com", "ValidPass123!", "회원");
        String token = loginToken("deleted@example.com");
        users.delete(users.findByEmail("deleted@example.com").orElseThrow());
        users.flush();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void currentUserComesFromTokenNotRequestParameter() throws Exception {
        signup("one@example.com", "ValidPass123!", "첫째");
        signup("two@example.com", "ValidPass123!", "둘째");
        Long other = users.findByEmail("two@example.com").orElseThrow().getId();
        mvc.perform(get("/api/users/me").param("userId", other.toString())
                        .header("Authorization", "Bearer " + loginToken("one@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value("one@example.com"));
    }

    @Test void logoutRejectsSameTokenAcrossProtectedApis() throws Exception {
        signup("logout@example.com", "ValidPass123!", "회원");
        String token = loginToken("logout@example.com");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        for (String path : List.of("/api/users/me", "/api/users/me/photos",
                "/api/users/me/training-sessions", "/api/users/me/training-sessions/1")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
        }
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void logoutDoesNotRevokeOtherTokensAndUserCanLoginAgain() throws Exception {
        signup("logout-one@example.com", "ValidPass123!", "회원");
        signup("logout-two@example.com", "ValidPass123!", "다른 회원");
        String first = loginToken("logout-one@example.com");
        String second = loginToken("logout-one@example.com");
        String otherUser = loginToken("logout-two@example.com");
        assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        for (String token : List.of(second, otherUser, loginToken("logout-one@example.com"))) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test void logoutRequiresValidUnexpiredToken() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        for (String token : List.of("invalid", signedToken("1", "memory-recall-test", "memory-recall-api",
                Instant.now().minusSeconds(10)))) {
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void logoutSupportsExistingTokensWithoutJti() throws Exception {
        signup("legacy@example.com", "ValidPass123!", "회원");
        String id = users.findByEmail("legacy@example.com").orElseThrow().getId().toString();
        String token = signedToken(id, "memory-recall-test", "memory-recall-api", Instant.now().plusSeconds(100));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/users/me").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
