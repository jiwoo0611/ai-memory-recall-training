# 마이페이지 조회 API

DB 테이블·Entity·마이그레이션·JWT 구조·의존성은 기존 구현을 유지합니다.
기존 회원가입의 UserResponse는 그대로 두고, 회원 조회에 UserProfileResponse를 사용합니다.
이번 변경은 소스 및 테스트 환경에 적용했습니다. 이미 실행 중인 로컬 서버는 재시작하지 않았으므로 새 코드는 자동 반영되지 않습니다.
실제 MySQL에는 테스트 데이터를 넣거나 수정하지 않았습니다.

## 변경 파일

추가:

- `service/MyPageService.java`: 읽기 전용 트랜잭션에서 소유자 확인, 목록 조회, 상세 DTO 조합.
- `dto/UserProfileResponse.java`: 기본 회원정보와 가입일·수정일.
- `dto/PhotoSummaryResponse.java`: 사진 목록의 공개할 필드.
- `dto/TrainingSessionSummaryResponse.java`: 훈련 및 사진 기본정보.
- `dto/TrainingSessionDetailResponse.java`: 질문별 답변과 결과를 담는 상세 응답. 하위 DTO는 내부 record로 정의.
- `dto/PageResponse.java`: content/page/size/totalElements/totalPages/hasNext.
- `src/test/java/com/konkuk/memory/MyPageIntegrationTest.java`: 테스트 H2의 서로 다른 두 사용자로 API 검증.
- 이 문서.

수정:

- `controller/UserController.java`: 기존 /me 확장 및 조회 경로 3개 추가.
- `service/UserService.java`: 프로필 응답과 현재 사용자 존재 확인 메서드.
- `repository/TrainingSessionRepository.java`: 소유자 조건은 유지하고 EntityGraph로 사진을 함께 조회.
- `exception/GlobalExceptionHandler.java`: 잘못된 숫자 파라미터를 400으로 반환.
- `src/test/java/com/konkuk/memory/ServerSmokeTest.java`: 확장된 회원 조회 DTO 반영.
- `README.md`: API 및 구현 범위 갱신.

## 인증과 소유권

모든 요청에 `Authorization: Bearer <accessToken>`을 넣습니다.
Controller가 `@AuthenticationPrincipal Jwt`의 subject에서 사용자 ID를 얻습니다.
클라이언트의 userId는 입력으로 사용하지 않습니다. 삭제된 사용자 토큰은 401입니다.
세션 상세는 `findByIdAndPhoto_User_Id`로 세션 소유자를 먼저 검사합니다.
질문·답변·결과도 기존 Repository의 세션+소유자 조건 메서드로 조회합니다.
다른 사용자 세션과 존재하지 않는 세션은 모두 404 / TRAINING_SESSION_NOT_FOUND입니다.
Entity를 직접 JSON으로 반환하지 않으며 password/passwordHash/storageKey는 DTO에 없습니다.

## API

| Method | URL | 응답 |
|---|---|---|
| GET | /api/users/me | UserProfileResponse |
| GET | /api/users/me/photos?page=0&size=20 | PageResponse of PhotoSummaryResponse |
| GET | /api/users/me/training-sessions?page=0&size=20 | PageResponse of TrainingSessionSummaryResponse |
| GET | /api/users/me/training-sessions/{sessionId} | TrainingSessionDetailResponse |

모두 요청 본문은 없습니다. 성공은 200, JWT 없음/잘못됨/만료는 401입니다.
page 기본값 0, size 기본값 20, size 범위 1~100입니다. 음수 page나 잘못된 숫자는 400입니다.
JPA에서 처리할 수 없는 과도한 offset(page * size)도 400입니다.
sessionId는 양수여야 하며 숫자 형식/범위가 잘못되면 400입니다.
사진은 createdAt DESC, id DESC; 훈련은 startedAt DESC, id DESC로 정렬합니다.
데이터 없음/범위를 벗어난 페이지는 200과 빈 content를 반환합니다. 총개수는 본인 데이터만 셉니다.

### 회원 정보 예시

```json
{
  "id": 1,
  "name": "홍길동",
  "email": "student@example.com",
  "createdAt": "2026-09-23T02:00:00Z",
  "updatedAt": "2026-09-23T02:00:00Z"
}
```

### 사진 목록 예시

```json
{
  "content": [{
    "id": 10,
    "originalFilename": "travel.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 102400,
    "createdAt": "2026-09-23T03:00:00Z",
    "confirmedTakenOn": null,
    "confirmedLocation": null
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

목록에는 이미지 URL을 포함하지 않습니다. 사진 업로드와 원본 파일 제공 기능은 아직 구현하지 않았습니다.
확인한 날짜·장소가 없으면 null입니다. AI 추정 정보를 확인 정보처럼 사용하지 않습니다.

### 훈련 목록 예시

```json
{
  "content": [{
    "id": 100,
    "photo": {"id": 10, "originalFilename": "travel.jpg"},
    "status": "IN_PROGRESS",
    "startedAt": "2026-09-23T03:10:00Z",
    "completedAt": null
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

### 훈련 상세 예시

```json
{
  "id": 100,
  "photo": {"id": 10, "originalFilename": "travel.jpg"},
  "status": "COMPLETED",
  "startedAt": "2026-09-23T03:10:00Z",
  "completedAt": "2026-09-23T03:15:00Z",
  "questions": [{
    "id": 1000,
    "questionOrder": 1,
    "content": "어떤 기억이 떠오르나요?",
    "type": "EXPERIENCE",
    "answer": {
      "id": 2000,
      "status": "ANSWERED",
      "content": "친구와 여행했던 기억이 납니다.",
      "createdAt": "2026-09-23T03:11:00Z"
    }
  }],
  "result": {
    "id": 3000,
    "feedbackStatus": "COMPLETED",
    "summary": "친구와 여행했던 경험을 회상했습니다.",
    "answeredCount": 1,
    "cannotRecallCount": 0,
    "mismatchCount": 0
  }
}
```

질문은 questionOrder 오름차순입니다. 생성 전에는 questions가 빈 배열입니다.
답변이 없으면 해당 question.answer=null, 결과가 없으면 result=null입니다.
CANNOT_RECALL / QUESTION_MISMATCH 답변은 선택 상태와 content=null을 그대로 반환합니다.
피드백 PENDING/FAILED도 정상 조회하며 답변을 숨기지 않습니다.
조회는 기존 요약/집계 값을 그대로 반환합니다. 질문/답변/결과를 새로 만들거나 계산해 저장하지 않습니다.

## 검증

최종 검증 완료: 2026-09-23 14:32 KST. Gradle `build` 성공, 총 35개 테스트 통과(실패/오류 0).

| 테스트 | 개수 | 결과 |
|---|---:|---|
| MyPageIntegrationTest | 15 | 통과 |
| AuthIntegrationTest | 12 | 통과 |
| DatabaseIntegrationTest | 7 | 통과 |
| ServerSmokeTest | 1 | 통과 |

중간 재실행에서 이전 결과 디렉터리의 파일 잠금으로 정리 단계가 실패했습니다.
잠긴 파일이나 실제 서버를 건드리지 않고 새 격리 빌드 디렉터리에서 최종 빌드와 테스트를 완료했습니다.

MyPageIntegrationTest는 H2에 테스트 데이터를 준비한 뒤 MockMvc와 실제 서명 JWT를 사용합니다.
HTTP 요청을 테스트 전체 트랜잭션으로 감싸지 않으므로 서비스 트랜잭션 종료 후 DTO 직렬화도 확인합니다.
기존 AuthIntegrationTest / DatabaseIntegrationTest / ServerSmokeTest도 함께 실행합니다.
ServerSmokeTest만 임의의 테스트 포트에 서버를 띄우며 현재 8080 서버에는 요청하지 않습니다.

확인 항목: 무인증/잘못된 토큰, 회원정보, 소유자별 목록과 총개수, 정렬/페이지,
본인 상세, 타인/없는 세션 404, 재훈련 기록 분리, 미완료 상태, 피드백 실패,
파라미터 400, 삭제된 사용자, 민감 필드 제외, 조회 전후 저장 데이터 불변.

일반 테스트 명령은 `./gradlew.bat --gradle-user-home .gradle-home --no-daemon --offline test`입니다.
이번 작업의 검증은 실행 중인 서버의 클래스/리소스를 덮어쓰지 않도록 별도 build/mypage-final-verification 경로에서 수행합니다.
결과: `build/mypage-final-verification/reports/tests/test/index.html`.

## 아직 구현하지 않은 것

프론트엔드, 사진 업로드/파일 조회, 사진 분석, 질문 생성, 답변 제출/평가, 결과 생성,
회원정보 수정/탈퇴, 토큰 갱신은 구현하지 않았습니다. 이후 추가된 서버 측 로그아웃은 [로그아웃 문서](LOGOUT.md)를 참고하세요.
새 기능의 실제 실행 서버 반영에는 나중에 별도 재시작/배포가 필요합니다. 이번 작업에서는 수행하지 않습니다.

## 사용자가 나중에 실행 서버에 반영하는 순서

1. 현재 서버를 실행한 터미널에서 Ctrl+C로 정상 종료합니다. MySQL은 종료할 필요가 없습니다.
2. 실행할 PowerShell에 DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET을 설정합니다.
   숨김 입력 방법은 LOCAL_MYSQL.md를 참고하세요. 기존 서버를 별도 자식 PowerShell에서 실행했다면 해당 환경변수가 부모 터미널에 남아 있지 않을 수 있습니다.
3. 프로젝트 폴더에서 아래 최종 검증된 JAR를 실행합니다. Start-LocalMySqlVerification.ps1은 다시 실행할 필요가 없습니다.

```powershell
java -jar .\build\mypage-final-verification\libs\memory-recall-backend-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080
```

4. 로그인 API로 토큰을 받은 뒤 마이페이지 API에 Bearer 토큰으로 접근합니다.
   JWT 키를 새로 만들었다면 기존 토큰은 사용할 수 없으므로 다시 로그인해야 합니다.

DB 스키마 변경이나 마이그레이션 추가는 없습니다. 사진/훈련 레코드가 없는 사용자는 빈 목록을 받는 것이 정상입니다.
위 서버 반영 절차는 안내만 작성했으며 자동 실행하지 않았습니다.
