package com.konkuk.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Opt-in local verification tool. No DROP/DELETE/UPDATE statements; no secret values in its report. */
public class LocalMySqlVerification {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final Map<String, Boolean> CHECKS = new LinkedHashMap<>();
    private static final String URL = "jdbc:mysql://localhost:3306/memory_recall?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    private static final String BASE = "http://127.0.0.1:8080";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("build"));
        REPORT.put("startedAt", Instant.now().toString());
        REPORT.put("status", "STARTING");
        REPORT.put("checks", CHECKS);
        saveReport();
        ConfigurableApplicationContext context = null;
        try {
            if (!URL.equals(System.getenv("DB_URL")) || !"root".equals(System.getenv("DB_USERNAME"))) {
                throw new IllegalStateException("Use scripts/Start-LocalMySqlVerification.ps1; target must be local memory_recall.");
            }
            if (System.getenv("DB_PASSWORD") == null || System.getenv("JWT_SECRET") == null) {
                throw new IllegalStateException("DB_PASSWORD and JWT_SECRET must be set locally.");
            }
            try (Connection db = connect()) {
                REPORT.put("database", rows(db, "SELECT DATABASE() AS database_name, VERSION() AS server_version"));
                REPORT.put("initialTables", rows(db, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'memory_recall' ORDER BY TABLE_NAME"));
                require("mysqlConnection", "memory_recall".equals(db.getCatalog()));
            }
            // Ensure this explicit verification run cannot be redirected by unrelated Spring settings.
            SpringApplication application = new SpringApplication(MemoryRecallApplication.class);
            context = application.run(
                    "--spring.profiles.active=local-mysql-verification",
                    "--spring.datasource.url=" + URL, "--spring.datasource.username=root",
                    "--spring.datasource.password=${DB_PASSWORD}", "--app.jwt.secret=${JWT_SECRET}",
                    "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "--spring.flyway.url=" + URL, "--spring.flyway.user=root",
                    "--spring.flyway.password=${DB_PASSWORD}",
                    "--spring.flyway.default-schema=memory_recall", "--spring.flyway.schemas=memory_recall",
                    "--spring.flyway.create-schemas=false", "--spring.flyway.clean-disabled=true",
                    "--spring.flyway.locations=classpath:db/migration", "--spring.flyway.enabled=true",
                    "--spring.jpa.hibernate.ddl-auto=validate", "--server.address=127.0.0.1", "--server.port=8080",
                    "--debug=false", "--trace=false", "--logging.level.root=INFO");
            require("serverStarted", context.isActive());
            try (Connection db = connect()) {
                verifySchema(db);
                verifyApisAndStoredUser(db);
            }
            REPORT.put("status", "PASSED");
            REPORT.put("completedAt", Instant.now().toString());
            saveReport();
            System.out.println("MYSQL VERIFICATION PASSED. Sanitized report: build/local-mysql-verification.json");
            System.out.println("Server remains running at " + BASE + ". Stop with Ctrl+C.");
        } catch (Throwable error) {
            REPORT.put("status", "FAILED");
            REPORT.put("errorType", error.getClass().getName());
            // Exception messages may contain connection details. Keep the report free of credentials.
            if (error instanceof SQLException sql) {
                REPORT.put("sqlState", sql.getSQLState());
                REPORT.put("sqlErrorCode", sql.getErrorCode());
            }
            if (error instanceof VerificationFailure) REPORT.put("failedCheck", error.getMessage());
            saveReport();
            if (context != null) context.close();
            System.err.println("Verification failed: " + error.getClass().getSimpleName() + ". See sanitized report and startup log.");
            System.exit(1);
        }
    }

    private static Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", System.getenv("DB_PASSWORD"));
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        return DriverManager.getConnection(URL, properties);
    }

    private static void verifySchema(Connection db) throws Exception {
        List<Map<String, Object>> tables = rows(db, "SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'memory_recall' ORDER BY TABLE_NAME");
        REPORT.put("tables", tables);
        Set<String> names = new HashSet<>();
        tables.forEach(row -> names.add(row.get("TABLE_NAME").toString()));
        require("requiredTables", names.containsAll(Set.of("users", "photos", "training_sessions", "questions", "answers", "results", "flyway_schema_history")));
        REPORT.put("columns", rows(db, "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'memory_recall' ORDER BY TABLE_NAME, ORDINAL_POSITION"));
        REPORT.put("indexes", rows(db, "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'memory_recall' ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX"));
        REPORT.put("foreignKeys", rows(db, "SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = 'memory_recall' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME"));
        REPORT.put("constraints", rows(db, "SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = 'memory_recall' ORDER BY TABLE_NAME, CONSTRAINT_NAME"));
        var migrations = rows(db, "SELECT version, description, script, success FROM memory_recall.flyway_schema_history ORDER BY installed_rank");
        REPORT.put("flyway", migrations);
        require("flywayV1Applied", scalar(db, "SELECT COUNT(*) FROM memory_recall.flyway_schema_history WHERE version = '1' AND success = 1") == 1);
        require("sixPrimaryKeys", scalar(db, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='memory_recall' AND CONSTRAINT_TYPE='PRIMARY KEY' AND TABLE_NAME IN ('users','photos','training_sessions','questions','answers','results')") == 6);
        require("emailUnique", scalar(db, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='memory_recall' AND TABLE_NAME='users' AND INDEX_NAME='uk_users_email' AND COLUMN_NAME='email' AND NON_UNIQUE=0") == 1);
        require("userRequiredColumns", scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='memory_recall' AND TABLE_NAME='users' AND COLUMN_NAME IN ('id','email','password_hash','name','created_at','updated_at') AND IS_NULLABLE='NO'") == 6);
        for (String[] edge : List.of(new String[]{"photos","user_id","users"}, new String[]{"training_sessions","photo_id","photos"}, new String[]{"questions","training_session_id","training_sessions"}, new String[]{"answers","question_id","questions"}, new String[]{"results","training_session_id","training_sessions"})) {
            try (PreparedStatement statement = db.prepareStatement("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='memory_recall' AND TABLE_NAME=? AND COLUMN_NAME=? AND REFERENCED_TABLE_SCHEMA='memory_recall' AND REFERENCED_TABLE_NAME=? AND REFERENCED_COLUMN_NAME='id'")) {
                for (int i = 0; i < 3; i++) statement.setString(i + 1, edge[i]);
                try (ResultSet result = statement.executeQuery()) { result.next(); require("ownerFk_" + edge[0], result.getLong(1) == 1); }
            }
        }
    }

    private static void verifyApisAndStoredUser(Connection db) throws Exception {
        String email = "mysql-check-" + UUID.randomUUID() + "@example.com";
        // This is a synthetic account password, NOT the user's MySQL password. Never persisted in a file.
        String password = UUID.randomUUID() + "Aa1!";
        REPORT.put("testAccountEmail", email);
        long before = scalar(db, "SELECT COUNT(*) FROM memory_recall.users");
        var signup = request("POST", "/api/auth/signup", Map.of("email", email, "password", password, "name", "MySQL verification"), null);
        require("signup201", signup.statusCode() == 201);
        JsonNode user = JSON.readTree(signup.body());
        require("signupNoPassword", noPasswordFields(user));
        require("duplicateSignup409", request("POST", "/api/auth/signup", Map.of("email", email, "password", password, "name", "MySQL verification"), null).statusCode() == 409);
        require("invalidInput400", request("POST", "/api/auth/signup", Map.of("email", "bad-email", "password", "short", "name", ""), null).statusCode() == 400);
        require("missingInput400", request("POST", "/api/auth/signup", Map.of(), null).statusCode() == 400);
        var login = request("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
        require("login200", login.statusCode() == 200);
        JsonNode tokenResponse = JSON.readTree(login.body());
        String token = tokenResponse.path("accessToken").asText();
        require("jwtIssued", token.split("\\.").length == 3 && "Bearer".equals(tokenResponse.path("tokenType").asText()));
        require("wrongPassword401", request("POST", "/api/auth/login", Map.of("email", email, "password", "WrongPassword123!"), null).statusCode() == 401);
        require("unknownUser401", request("POST", "/api/auth/login", Map.of("email", "absent-" + UUID.randomUUID() + "@example.com", "password", password), null).statusCode() == 401);
        var me = request("GET", "/api/users/me", null, token);
        JsonNode meJson = JSON.readTree(me.body());
        require("jwtMe200", me.statusCode() == 200);
        require("jwtIdentifiesOwner", meJson.path("id").asLong() == user.path("id").asLong() && email.equals(meJson.path("email").asText()));
        require("meNoPassword", noPasswordFields(meJson));
        require("noJwt401", request("GET", "/api/users/me", null, null).statusCode() == 401);
        require("invalidJwt401", request("GET", "/api/users/me", null, "invalid.jwt.value").statusCode() == 401);
        String[] parts = token.split("\\.");
        String altered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        require("tamperedJwt401", request("GET", "/api/users/me", null, altered).statusCode() == 401);
        try (PreparedStatement query = db.prepareStatement("SELECT id, password_hash, created_at, updated_at FROM memory_recall.users WHERE email=?")) {
            query.setString(1, email);
            try (ResultSet result = query.executeQuery()) {
                require("userStoredInMySql", result.next());
                String hash = result.getString("password_hash");
                require("bcryptStored", hash.startsWith("$2") && hash.length() == 60 && !hash.equals(password));
                require("bcryptMatchesPassword", new BCryptPasswordEncoder().matches(password, hash));
                require("timestampsStored", result.getTimestamp("created_at") != null && result.getTimestamp("updated_at") != null);
                REPORT.put("testAccountId", result.getLong("id"));
                require("oneRowForEmail", !result.next());
            }
        }
        require("exactlyOneAccountAdded", scalar(db, "SELECT COUNT(*) FROM memory_recall.users") == before + 1);
    }

    private static boolean noPasswordFields(JsonNode json) {
        if (json.isObject()) {
            Iterator<String> names = json.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (name.toLowerCase(Locale.ROOT).contains("password") || !noPasswordFields(json.get(name))) return false;
            }
        } else if (json.isArray()) { for (JsonNode element : json) if (!noPasswordFields(element)) return false; }
        return true;
    }

    private static HttpResponse<String> request(String method, String path, Map<String, String> body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<Map<String, Object>> rows(Connection db, String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = db.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private static long scalar(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement(); ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }

    private static void require(String name, boolean passed) throws Exception {
        CHECKS.put(name, passed);
        saveReport();
        if (!passed) throw new VerificationFailure(name);
    }

    private static void saveReport() throws Exception {
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("build/local-mysql-verification.json").toFile(), REPORT);
    }

    private static class VerificationFailure extends RuntimeException {
        VerificationFailure(String check) { super(check); }
    }
}
