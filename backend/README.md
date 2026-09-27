`# 사진 기반 기억 회상 서비스 — 백엔드

현재 로컬 MySQL 9.2 / root / memory_recall 연결 및 실제 검증 절차는 [로컬 MySQL 실행 안내](docs/LOCAL_MYSQL.md)를 참고하세요.
아래의 memory_app 생성 안내는 별도 개발 계정을 만들 때의 예시이며, 이미 생성한 로컬 DB를 다시 만들 필요는 없습니다.

`project konkuk.txt`를 기준으로 회원가입, 로그인, JWT 인증, 앨범·사진 관리, Gemini 사진 분석, 앨범 단위 질문 생성, 마이페이지 조회 API와 프론트 연결을 구현했습니다.
답변 제출·평가와 결과 생성은 후속 구현 범위입니다.
`GET /api/users/me`는 id/name/email/createdAt/updatedAt을 반환합니다.
마이페이지의 추가·수정 파일, API 응답, 테스트 방법은 [마이페이지 API 문서](docs/MY_PAGE.md)를 참고하세요.

기존 기획서는 수정하지 않았습니다. 작업 시작 당시 `사진 기반 기억 회상 플랫폼.txt`라는 별도 파일은 폴더에 없었습니다.

## 1. 기술과 선택 이유

| 기술                             | 선택 이유                                                                                                 |
| -------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Java 17                          | 현재 PC에 설치된 LTS Java를 그대로 사용합니다.                                                            |
| Spring Boot 3.5.16               | Java 17과 호환되는 3.5 계열 정식 릴리스입니다. 의존성 버전을 함께 관리합니다.                             |
| Gradle 8.14.3 Wrapper            | 별도 Gradle 설치 없이 팀원이 같은 버전으로 빌드합니다. 배포본 SHA-256을 검증합니다.                       |
| Spring Web                       | JSON REST API와 내장 Tomcat 서버를 제공합니다.                                                            |
| Spring Data JPA                  | Java Entity를 DB 테이블과 연결하고 Repository로 조회합니다.                                               |
| Spring Security                  | 인증 필터, 비밀번호 처리, 접근 제어를 제공합니다. BCrypt가 포함되어 별도 BCrypt 라이브러리는 없습니다.    |
| OAuth2 Resource Server starter   | Spring Security의 검증된 JWT 처리와 Nimbus JOSE 라이브러리를 사용합니다. 외부 OAuth 서버는 필요 없습니다. |
| Validation                       | 이메일 형식, 빈 값, 길이를 검사합니다.                                                                    |
| MySQL Connector/J                | 실제 MySQL 연결에 사용합니다.                                                                             |
| Flyway core + MySQL 지원         | SQL 변경 이력을 버전별로 관리합니다. 팀원 DB 구조가 달라지는 일을 줄입니다.                               |
| Spring Boot Test / Security Test | HTTP, 인증 및 DB 연동을 검증합니다.                                                                       |
| H2 (테스트 전용)                 | MySQL 비밀번호 없이 격리된 자동 테스트를 실행합니다. 실제 서버에는 포함되지 않습니다.                     |

버전 기준: [Spring Boot 3.5 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[Spring Security JWT 문서](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
Boot가 관리하는 라이브러리에 개별 버전을 덧붙이지 않았습니다. Lombok, Redis, Docker, 별도 AI 라이브러리는 필요하지 않아 추가하지 않았습니다.

## 2. 전체 소스 구조와 역할

```text
project/
├── project konkuk.txt                     # 기존 공식 문서, 변경하지 않음
├── .gitignore
├── .env.example                          # 환경변수 안내, 실제 비밀값 없음
├── build.gradle                          # 의존성 및 빌드 설정
├── settings.gradle
├── gradlew / gradlew.bat                  # Linux/macOS / Windows 빌드 실행기
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── README.md
├── docs/
│   ├── DATABASE.md                       # 테이블 설계, ERD, 팀원 연동 규칙
│   ├── LOCAL_MYSQL.md                    # 로컬 MySQL 검증 절차
│   └── MY_PAGE.md                        # 마이페이지 API와 응답 예시
└── src/
    ├── main/
    │   ├── java/com/konkuk/memory/
    │   │   ├── MemoryRecallApplication.java
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   └── UserController.java
    │   │   ├── service/
    │   │   │   ├── AuthService.java
    │   │   │   ├── MyPageService.java
    │   │   │   └── UserService.java
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   ├── PhotoRepository.java
    │   │   │   ├── TrainingSessionRepository.java
    │   │   │   ├── QuestionRepository.java
    │   │   │   ├── AnswerRepository.java
    │   │   │   └── ResultRepository.java
    │   │   ├── entity/
    │   │   │   ├── BaseEntity.java
    │   │   │   ├── User.java
    │   │   │   ├── Photo.java
    │   │   │   ├── TrainingSession.java
    │   │   │   ├── Question.java
    │   │   │   ├── Answer.java
    │   │   │   └── Result.java
    │   │   ├── dto/
    │   │   │   ├── SignupRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── UserResponse.java
    │   │   │   ├── UserProfileResponse.java
    │   │   │   ├── PageResponse.java
    │   │   │   ├── PhotoSummaryResponse.java
    │   │   │   ├── TrainingSessionSummaryResponse.java
    │   │   │   ├── TrainingSessionDetailResponse.java
    │   │   │   └── TokenResponse.java
    │   │   ├── security/
    │   │   │   ├── TokenService.java
    │   │   │   └── SecurityErrorHandler.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtProperties.java
    │   │   │   └── CorsProperties.java
    │   │   └── exception/
    │   │       ├── ApiException.java
    │   │       ├── ApiError.java
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__create_core_tables.sql
    └── test/
        ├── java/com/konkuk/memory/
        │   ├── AuthIntegrationTest.java
        │   ├── DatabaseIntegrationTest.java
        │   ├── MyPageIntegrationTest.java
        │   ├── LocalMySqlVerification.java
        │   └── ServerSmokeTest.java
        └── resources/application-test.yml
```

`controller`는 HTTP 요청/응답, `service`는 처리 순서와 트랜잭션, `repository`는 DB 조회,
`entity`는 저장 구조, `dto`는 API에서 공개할 데이터, `security`는 토큰 발급/인증 오류,
`config`는 설정, `exception`은 일관된 오류 응답을 담당합니다.
요청한 역할 구분을 그대로 따랐으며 복잡한 여러 서버 구조로 나누지 않았습니다.
빌드 후 생기는 `build/`, `.gradle/`, `.gradle-home/`은 생성물/캐시이며 Git에서 제외됩니다.

## 3. API와 처리 흐름

| Method | URL                                           | 인증            | 성공 응답                              |
| ------ | --------------------------------------------- | --------------- | -------------------------------------- |
| POST   | `/api/auth/signup`                            | 불필요          | 201, id/email/name                     |
| POST   | `/api/auth/login`                             | 불필요          | 200, accessToken/tokenType/expiresIn   |
| GET    | `/api/users/me`                               | Bearer JWT 필수 | 200, id/name/email/createdAt/updatedAt |
| GET    | `/api/users/me/photos`                        | Bearer JWT 필수 | 200, 본인 사진 목록 및 페이지 정보     |
| GET    | `/api/users/me/training-sessions`             | Bearer JWT 필수 | 200, 본인 훈련 목록 및 페이지 정보     |
| GET    | `/api/users/me/training-sessions/{sessionId}` | Bearer JWT 필수 | 200, 본인 훈련의 사진·질문·답변·결과   |
| POST   | `/api/albums/{albumId}/questions/generate`    | Bearer JWT 필수 | 200, 앨범 통합 근거로 생성한 질문 3개   |

회원가입: JSON → 이메일 공백 제거·소문자 통일 / 이름 바깥 공백 제거 → Validation →
BCrypt 72바이트 제한 확인 → 이메일 중복 확인 → BCrypt(cost 12) 해시 → DB 저장 → 사용자 DTO 반환.
비밀번호는 공백을 제거하거나 변형하지 않습니다. 기본 길이는 8~72자이며 UTF-8로도 72바이트 이하여야 합니다.
예를 들어 한글은 보통 글자당 3바이트이므로 문자 수만으로 판단하지 않습니다.
동시 가입 요청은 DB의 UNIQUE가 최종적으로 차단합니다.

로그인: 이메일 정규화 → 사용자 조회 → BCrypt.matches → 성공하면 HS256 JWT 발급.
없는 이메일과 잘못된 비밀번호는 같은 오류를 반환하고, 없는 사용자도 더미 해시 검증을 수행합니다.

인증: `Authorization: Bearer <accessToken>` → Spring Security가 서명·만료·issuer·audience·사용자 ID 형식 검사 →
JWT의 `sub`를 현재 사용자 ID로 사용 → `/me`가 실제 사용자 존재 여부를 조회 → DTO 반환.
요청 파라미터로 보낸 사용자 ID를 현재 사용자로 신뢰하지 않습니다.
JWT는 기본 1,800초(30분), 설정 가능 범위 60~3,600초입니다. 토큰에는 이메일/이름/비밀번호를 담지 않습니다.
JWT 내용은 암호문이 아니며 서명으로 위변조를 확인하는 구조입니다.

대표 오류:

| HTTP | 상황                                   | code                          |
| ---- | -------------------------------------- | ----------------------------- |
| 400  | 필수값/이메일/길이 오류                | VALIDATION_ERROR              |
| 400  | 잘못된 JSON 또는 예상하지 않은 필드    | INVALID_JSON                  |
| 400  | 비밀번호 UTF-8 72바이트 초과           | PASSWORD_TOO_LONG             |
| 401  | 이메일/비밀번호 불일치                 | INVALID_CREDENTIALS           |
| 401  | 토큰 없음·변조·만료 또는 삭제된 사용자 | UNAUTHORIZED                  |
| 403  | 허용되지 않은 접근/Origin              | FORBIDDEN 또는 CORS 필터 응답 |
| 409  | 이미 가입된 이메일                     | EMAIL_ALREADY_EXISTS          |
| 409  | 동시 가입의 DB UNIQUE 충돌 등          | DATA_CONFLICT                 |

인증/입력 오류는 `timestamp, status, code, message, fieldErrors` 형식입니다.
검증 오류에 비밀번호 원문/거절된 입력값을 포함하지 않습니다. CORS 거절 등 프레임워크 자체 응답은 형식이 다를 수 있습니다.

## 4. MySQL에서 직접 할 설정

MySQL 8.4 LTS 사용을 권장하며 CHECK 제약을 실제 검사하는 MySQL 8.0.16 이상이 필요합니다.
제약조건 동작은 [MySQL 공식 CHECK 문서](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)를 기준으로 했습니다.
현재 PC의 서버 위치, 포트, 계정, 비밀번호는 확인하지 못했으므로 임의로 연결하지 않았습니다.
MySQL Workbench 등에서 관리자 계정으로 다음을 실행하세요. 비밀번호 문구는 반드시 직접 바꾸세요.

```sql
CREATE DATABASE memory_recall
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'memory_app'@'localhost' IDENTIFIED BY '직접_정한_강한_비밀번호';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON memory_recall.* TO 'memory_app'@'localhost';
```

위 권한은 이 프로젝트 DB에 한정된 개발용 권한입니다. Flyway가 테이블을 생성하므로 CREATE/ALTER 등이 필요합니다.
원격 DB라면 호스트와 계정의 접속 허용 범위를 실제 서버에 맞추세요. 원격 전송에는 TLS를 설정하세요.
Flyway는 첫 실행 시 6개 업무 테이블과 `flyway_schema_history` 관리 테이블을 생성합니다.
SQL 파일을 수동으로 먼저 실행할 필요가 없습니다.
기존에 같은 이름의 테이블이 있는 DB를 재사용하지 말고 새 빈 DB로 시작하세요.

## 5. 환경변수와 실행 (Windows PowerShell)

프로젝트 폴더에서 다음 환경변수를 설정한 **같은 터미널**에서 실행하세요.
`.env.example`은 안내용이며 Spring Boot가 `.env`를 자동으로 읽지는 않습니다.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/memory_recall?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME = 'memory_app'

# 비밀번호를 터미널 기록에 직접 적지 않고 입력합니다.
$dbCredential = Get-Credential -UserName 'memory_app' -Message 'MySQL 비밀번호를 입력하세요'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password

# 최초 1회: 암호학적 난수 32바이트로 키를 만들고 현재 터미널 환경변수에만 설정합니다.
$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtKeyBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)

$env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000,http://localhost:5173'

.\gradlew.bat --gradle-user-home .gradle-home --no-daemon bootRun
```

첫 실행은 Gradle/라이브러리 다운로드 때문에 인터넷 연결과 시간이 필요합니다.
Windows Java 17의 한글 경로를 지원하도록 Wrapper는 운영체제 기본 인코딩을 유지하고,
Java 소스 컴파일 인코딩은 UTF-8로 따로 지정했습니다. Gradle 실행기에 `-Dfile.encoding=UTF-8`을
임의로 추가하면 현재 Windows 환경에서 테스트 실행기의 경로가 깨질 수 있습니다.
JWT 키는 실제 사용 시 안전한 비밀 설정에 보관해 재사용하세요. 다시 생성하면 기존 토큰은 모두 무효가 됩니다.
터미널을 닫으면 위에서 설정한 환경변수는 사라집니다. IDE로 실행할 때는 같은 변수들을 Run Configuration에 설정하되
비밀값이 포함된 설정을 Git에 커밋하지 마세요. 서버 종료는 `Ctrl+C`입니다.

빌드 및 자동 테스트:

```powershell
.\gradlew.bat --gradle-user-home .gradle-home --no-daemon clean build
```

빌드된 서버 실행(위 환경변수 설정 필요):

```powershell
java -jar build/libs/memory-recall-backend-0.0.1-SNAPSHOT.jar
```

`-plain.jar`는 실행용 서버가 아닙니다. 환경변수 누락, 약한/잘못된 형식의 JWT 키, DB 접속 실패,
스키마 불일치가 있으면 시작에 실패하도록 구성했습니다. 비밀번호·키를 기본값으로 대신하지 않습니다.
정상 시작 시 로그에 `Started MemoryRecallApplication`과 Tomcat 포트가 표시됩니다.

## 6. Postman 테스트

서버가 실행된 뒤 순서대로 진행합니다. 기본 주소는 `http://localhost:8080`입니다.

1. **회원가입**: `POST /api/auth/signup`, Body → raw → JSON.

```json
{
  "email": "student@example.com",
  "password": "StudyMemory123!",
  "name": "홍길동"
}
```

성공하면 201과 아래 형태를 받습니다. id는 DB가 정하므로 고정값이 아닙니다.

```json
{ "id": 1, "email": "student@example.com", "name": "홍길동" }
```

같은 요청을 다시 보내면 409, 잘못된 이메일이나 빈 이름은 400이어야 합니다.

2. **로그인**: `POST /api/auth/login`, Body → raw → JSON.

```json
{ "email": "student@example.com", "password": "StudyMemory123!" }
```

성공 응답:

```json
{ "accessToken": "발급된.JWT.문자열", "tokenType": "Bearer", "expiresIn": 1800 }
```

비밀번호를 틀리게 보내면 401이어야 합니다.

3. **인증 확인**: `GET /api/users/me`, Authorization 탭 → Type: Bearer Token →
   로그인 응답의 `accessToken` 문자열만 붙여넣습니다. 200과 본인의 id/name/email/createdAt/updatedAt을 확인합니다.
   직접 Header로 설정한다면 `Authorization: Bearer 실제토큰`입니다.

4. Authorization을 제거하거나 토큰을 변조하면 401인지 확인합니다.
   다른 이메일로 가입/로그인하여 `/me`가 두 번째 사용자 정보를 반환하는지도 확인하세요.
   토큰이 만료되면 다시 로그인합니다. 토큰과 실제 계정 비밀번호를 공개 Postman Workspace에 저장하지 마세요.

## 7. 검증 범위

마이페이지 추가 후 최종 검증: **35개 통과, 실패/오류 0**, Gradle build 성공.
마이페이지 15개와 기존 인증/DB/HTTP 서버 20개를 격리된 H2 환경에서 검증했습니다.
최종 결과는 `build/mypage-final-verification/reports/tests/test/index.html`입니다.
실제 실행 서버 반영 절차는 [마이페이지 문서](docs/MY_PAGE.md)를 참고하세요.

이전 1차 구현 검증 기록:

2026-09-23 현재 PC에서 `gradlew.bat --gradle-user-home .gradle-home --no-daemon --offline clean build` 성공:
인증 통합 테스트 12개, DB 통합 테스트 7개, 실제 HTTP 서버 테스트 1개 — **총 20개 통과, 실패 0개**.
실행용 JAR도 생성되었습니다. 테스트 서버는 테스트 종료 후 종료되었습니다.

자동 테스트는 H2의 MySQL 호환 모드에서 같은 Flyway 마이그레이션을 적용하고 JPA 매핑을 검증합니다.
회원가입/중복/입력 오류/BCrypt/로그인/실패/토큰 서명·만료·issuer·audience/다른 사용자 차단/
CORS/재훈련 독립성/DB UNIQUE와 FK를 검사합니다.
`ServerSmokeTest`는 임의의 실제 HTTP 포트에 내장 Tomcat을 띄워 가입 → 로그인 → 인증 조회까지 요청합니다.
테스트 결과는 `build/reports/tests/test/index.html`에서 볼 수 있습니다.

**H2 통과는 실제 MySQL 연결 및 MySQL 고유 동작 검증을 대신하지 않습니다.**
MySQL 계정 설정 후 서버를 실행하여 Flyway/JPA 검증과 위 Postman 절차를 실제 DB에서 수행해야 합니다.
테스트 키와 H2 설정은 `src/test`에만 있으며 실행용 JAR에는 포함되지 않습니다.

## 8. 팀원 연결과 다음 단계

Entity/Repository와 마이페이지 조회를 구현했습니다. 세부 컬럼과 ERD는 [DB 설계](docs/DATABASE.md)를 참고하세요.
다음 담당자는 같은 서버의 controller/service에서 로그인 사용자를 확인한 뒤 소유자 조건이 있는 Repository를 사용합니다.
프론트엔드와 AI 모듈에 DB 계정이나 JWT 서명 키를 전달하지 않습니다.
AI 연동은 백엔드 service가 호출하고 검증 후 저장하는 구조로 확장할 수 있습니다.

다음 구현 순서는 사진 업로드/파일 조회 → 세션/질문 저장 →
답변 중복 처리와 완료 트랜잭션 → 피드백 생성/재시도 → 기존 마이페이지 조회와 프론트 연결 순서를 권장합니다.
질문 3개, 유형과 평가 기준은 기획서상 협의안이므로 DB에 문항 수 3개를 강제로 고정하지 않았습니다.

POST /api/auth/logout으로 현재 토큰의 jti와 만료시간을 DB에 저장하여 재시작 후에도 차단합니다. 사용법은 [로그아웃 문서](docs/LOGOUT.md)를 참고하세요. Refresh token, 비밀번호 변경/재설정, 이메일 인증은 구현하지 않았습니다.
배포 전에 팀이 로그아웃 즉시 무효화 방식과 로그인 시도 제한, HTTPS, 프론트 토큰 보관 정책을 결정해야 합니다.
CORS는 브라우저의 교차 출처 접근 설정이며, 데이터 소유권 검사를 대신하지 않습니다.
현재 CSRF 비활성화는 쿠키를 쓰지 않는 Bearer 헤더 인증에 맞춘 설정입니다. 인증을 쿠키로 바꾸면 재검토해야 합니다.
