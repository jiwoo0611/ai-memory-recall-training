# 객관식 답변·결과 영구 저장

## 기존 구현 분석과 적용 범위

현재 체크아웃의 커밋은 로컬 `origin/backend-dongmin`과 동일하며, 기존 미커밋 회상 훈련 기능 위에 추가했다. 원격 fetch, 브랜치 전환, 커밋, push는 하지 않았다. 작업 범위는 `backend/`, `frontend/`이다.

기존 `QuizSubmissionService`는 read-only 트랜잭션에서 채점 응답만 생성했다. 기존 변경의 `UserController`는 그 응답의 선택지 문자열을 `RecallService`에 전달해 답변/주관식 집계/완료를 저장했지만, 선택지 번호와 객관식 점수는 보존하지 않았다. 기존 `QuizPage`는 채점 결과를 React 상태에만 유지하고, 다시 풀기에서 같은 세션의 화면 상태만 초기화했다. 현재 App의 퀴즈·훈련 주소는 RecallPage를 사용하므로, 그 안에서 객관식/기존 주관식 기록을 분기한다.

재사용한 기능: 사용자 소유권 쿼리, `lockOwned`, 세션별 질문 스냅샷, 기존 답변/결과 unique 제약, 재훈련 API 및 요청 UUID, 페이지 조회·사진 필터, 기존 피드백 트랜잭션 서비스, 기존 화면과 CSS. 기존 인증·사진 업로드·질문 생성 알고리즘은 변경하지 않았다.

## 저장과 트랜잭션

1. `/submit`은 로그인 JWT subject로 사용자 ID를 얻는다. `sessionId + photo.user.id` 조건으로 세션을 비관적 잠금 조회한다. 다른 사용자와 없는 ID는 모두 404다.
2. COMPLETED이면 동일 요청이라도 409 `ALREADY_SUBMITTED`다. 저장된 결과를 GET으로 조회한다. IN_PROGRESS 이외 상태 또는 이미 저장된 답변/결과가 있으면 409 `SESSION_NOT_READY`다.
3. 모든 질문 ID의 일치, 중복, 누락, null, 객관식 형식, 선택지 0~3 및 저장된 정답을 검증한다. 모든 채점이 끝난 뒤 저장한다.
4. Answer 전부, 객관식 Result 한 개(PENDING), 세션 COMPLETED 및 completedAt을 하나의 쓰기 트랜잭션으로 커밋한다. 실패하면 전부 롤백한다.
5. 컨트롤러가 별도 Spring 서비스 프록시의 피드백 메서드를 호출한다. 답변 저장 트랜잭션과 분리되어 있으며 내부 메서드 호출로 트랜잭션을 우회하지 않는다.
6. 피드백은 잠금으로 직렬화한다. PROCESSING에서 규칙 기반 생성 후 COMPLETED 또는 FAILED가 된다. 짧은 동기 처리이므로 PROCESSING은 보통 외부 조회에 보이지 않는다. 피드백 트랜잭션 자체의 DB 장애/프로세스 종료는 PENDING으로 남을 수 있으며 저장된 답변은 유지된다.

객관식 피드백 버전은 `quiz-rules-v1`이다. 저장된 응답 수·정답 수, 틀린 질문 번호·선택 답·정답만 사용한다. 외부 AI 호출 및 새로운 환경변수가 필요하지 않다. 기존 주관식 발췌 요약과 그 API의 동일 요청 재생 정책은 유지한다.

## DB 변경

새 파일: `V11__persist_quiz_scores.sql`. V1~V10 파일은 변경하지 않았다.

| 테이블 | 추가 컬럼 | 의미 |
|---|---|---|
| answers | selected_option_index INT NULL | 0부터 시작하는 선택지 번호 |
| answers | correct BOOLEAN NULL | 제출 시 확정한 정답 여부 |
| results | total_count INT NULL | 객관식 전체 문항 수 |
| results | correct_count INT NULL | 정답 수 |
| results | incorrect_count INT NULL | 오답 수 |

선택 문자열은 기존 `answers.content`, 저장 시각은 BaseEntity의 `created_at`, 세션 관계는 `answers.question_id → questions.training_session_id`를 사용한다. 질문/보기/정답은 수정 메서드가 없는 세션별 Question 스냅샷을 사용한다. 재훈련과 질문 생성은 새 세션·새 Question을 만들며 기존 Question을 수정하지 않는다.

`answered_count`, `cannot_recall_count`, `mismatch_count`는 기존 회상 훈련 호환을 위해 유지했다. 과거에 주관식으로 저장된 선택 문자열만으로 객관식 답안을 추측해 소급 채점하지 않는다. 이 경우 새 컬럼은 NULL이며 기존 화면으로 보여준다.

기존 unique 제약 `uk_answers_question`, `uk_results_session` 유지. FK 추가/삭제 없음. CHECK 제약은 객관식 선택 번호/정답 여부의 동시 존재, 선택 범위, 음수가 아닌 점수, `정답+오답=전체=응답`을 검증한다.

### 삭제 규칙과 데이터 손실 범위

기존 미커밋 V9 및 RECALL.md에는 사진 삭제 시 해당 기준 사진의 세션→질문→답변 및 결과를 함께 삭제하는 정책이 명시되어 있다. V10의 재훈련 요청 연결도 세션 삭제 시 제거된다. 기존 사진/앨범 삭제 및 스토리지 삭제 큐 정책을 유지했으며 새로운 cascade를 추가하지 않았다.

**사진·앨범 삭제 또는 별도 ‘퀴즈 초기화’는 관련 훈련 이력을 영구 삭제한다.** 새 훈련/다시 풀기는 이 동작을 사용하지 않고 기존 재훈련 API로 기록을 보존한다. 다른 사진을 근거로 포함한 질문 스냅샷은 기준 사진의 훈련이 삭제될 때까지 남는 기존 동작을 유지한다. 삭제 후에도 기록을 유지하는 정책이 필요하면 팀에서 별도로 결정해야 한다.

기존 V7은 특정 테스트 계정의 퀴즈 기록을 삭제하는 마이그레이션이다. 수정하지 않았으므로 배포 전 기존 적용 이력을 확인한다.

## API

모든 경로의 접두사는 `/api/users/me/training-sessions`이며 Bearer 인증이 필요하다. 기존 경로를 유지했다.

| Method | 경로 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|
| POST | `/{id}/submit` | `{ "answers": [{ "questionId": 1, "selectedOptionIndex": 0 }] }` | 기존 sessionId/totalCount/correctCount/incorrectCount/results + 저장된 상세 `session` | 400 검증, 401 인증, 404 소유권/없음, 409 완료/준비 안 됨 |
| GET | `?page=0&size=6` | page≥0, size 1~100 | content/page/size/totalElements/totalPages/hasNext, 목록별 result 집계 추가 | 400 페이지, 401 인증 |
| GET | `?photoId=1&page=0&size=6` | 로그인 사용자 사진 ID | 같은 사진의 독립된 세션 목록 | 위 오류 + 404 사진 소유권/없음 |
| GET | `/{id}` | 양수 ID | 사진, 상태, 시작/완료, questionCount/respondedCount, 질문/보기/저장 답변/채점, result/피드백 | 400 ID, 401 인증, 404 소유권/없음 |
| POST | `/{id}/feedback` | 본문 없음 | 갱신된 세션 상세 | 401 인증, 404 소유권/없음, 409 답변 미저장 |
| POST | `/{id}/retrain` | `{ "requestId": "UUID" }` | `{ "sessionId": 새ID }` | 400 UUID/질문 없음, 401 인증, 404 소유권/없음 |

페이지는 `startedAt DESC, id DESC`다. 빈 목록은 HTTP 200과 빈 content다. 상세 Answer에는 id/status/content/createdAt/selectedOptionIndex/correct/correctOptionIndex/correctOption이 포함된다. 답변 제출 전에는 answer가 null이므로 정답이 공개되지 않는다. Result는 기존 필드 외에 totalCount/correctCount/incorrectCount/generationVersion을 포함한다.

새 `/feedback/retry` 경로는 만들지 않았다. 기존 `/feedback`은 PENDING 생성 및 FAILED 재시도를 지원하고, 이미 COMPLETED이면 결과를 변경하지 않는다. 세션 잠금으로 중복 생성을 방지한다. 재훈련은 동일 source ID와 requestId를 재전송하면 같은 신규 세션 ID를 돌려준다.

## 프론트 동작

- 기존 `#/training/{id}`, `#/albums/{albumId}/quiz/{id}`를 사용한다.
- 새 객관식 세션과 채점된 객관식 결과는 QuizPage, 기존 주관식 기록은 RecallPage로 표시한다.
- 완료 결과는 상세 API에서 복원한다. 제출 버튼 및 선택지는 제출 중 비활성화한다. 실패 시 선택을 유지한다.
- 서버 커밋 후 응답이 유실되면 재제출의 409를 받아 저장된 상세 결과를 복구한다.
- 정답/오답, 모든 질문의 보기·사용자 선택·정답, 피드백 상태와 재시도를 표시한다.
- 마이페이지의 기존 로딩/실패/재시도/페이지 UI를 유지하고 점수 및 같은 사진 필터를 추가했다.

## 검증 명령

최종 검증 결과: 백엔드 전체 98개(새 객관식 통합 테스트 15개 포함) 성공, 실패/건너뜀 0개. Edge 브라우저 테스트 3개 성공. 프론트 의존성 설치 및 최종 Vite 프로덕션 빌드 성공. 실행 환경의 Gradle 캐시/Vite 파일 읽기 제한은 실행 권한을 조정해 해결했다. 초기 테스트 코드의 타입 변환 및 Mockito 추상 메서드 호출 오류는 수정했으며 최종 실패는 없다.

실제 사용 명령은 백엔드 `gradlew.bat test --offline --no-daemon`(기존 Gradle 캐시 사용), 프론트 `npm.cmd install --ignore-scripts --no-audit --no-fund`, `npm.cmd run build`, `npx.cmd playwright test --config playwright.quiz.config.js`이다. MySQL/R2/실제 외부 AI 호출과 서버 프로세스 재시작 후 파일 DB 조회는 이번 자동 검증에 포함하지 않았다.

PowerShell에서 저장소의 backend와 frontend를 각각 사용한다. JDK 17, Node 20.9 이상이 필요하다.

```powershell
cd backend
.\gradlew.bat test
cd ..\frontend
npm.cmd install
npm.cmd run build
npx.cmd playwright test --config playwright.quiz.config.js
```

브라우저 검증은 설치된 Microsoft Edge를 headless로 실행한다. 백엔드 18081/프론트 5174 포트와 별도 H2 메모리 DB를 사용하고 테스트 계정/질문을 자동 생성한다. 기존 서비스와 사용자 DB는 사용하지 않는다. 테스트 종료 시 DB는 사라진다. 사진 등록·외부 AI 질문 생성은 fixture로 대체하며, 그 이후 저장/조회/재훈련은 실제 API와 DB를 사용한다. 피드백 실패의 브라우저 표시만 응답을 대체하며, 실제 생성 실패 시 DB 보존은 통합 테스트에서 검증한다.

`QuizPersistenceIntegrationTest`는 커밋된 fixture와 별도 요청/트랜잭션으로 검증한다. 정상·중복·동시 제출, 잘못된 질문/누락/중복/null/선택 범위, 소유권, 답변 일부 쓰기 후 롤백, 결과 저장 실패 롤백, 피드백 실패/재시도, PENDING 조회, 빈 페이지, 최신순·사진별 재훈련 보존을 포함한다. MySQL 및 실제 프로세스 재시작은 아래 절차로 추가 확인한다.

## Windows 수동 실행 및 재시작 검증

터미널 1:

```powershell
cd 'C:\Users\정지우\Documents\ChatGPT\졸업프로젝트2\graduation_project\backend'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

터미널 2:

```powershell
cd 'C:\Users\정지우\Documents\ChatGPT\졸업프로젝트2\graduation_project\frontend'
npm.cmd install
$env:VITE_API_BASE_URL = 'http://localhost:18080'
npm.cmd run dev
```

1. `http://localhost:5173/#/login`에서 가입/로그인한다. 기존 local 프로필은 파일 DB `backend/local-data/memory`와 로컬 업로드를 사용한다. 기본 데모 질문은 주관식이므로 객관식 검증에는 기존 객관식 기록 또는 실제 질문 생성이 필요하다.
2. 앨범·사진 선택 → 기존 사진 확인/질문 생성 → 객관식 답안 선택 → 제출한다. 질문 생성에는 기존 GEMINI_API_KEY 설정이 필요하지만 답변 저장/규칙 기반 피드백에는 필요하지 않다.
3. 세션 URL, 선택 답, 점수, 완료 시각을 기록한다. 새로고침 후 값이 같은지 확인한다.
4. 로그아웃·재로그인 후 마이페이지 상세 및 같은 사진 기록에서 같은 결과를 확인한다.
5. ‘같은 사진으로 새 훈련’을 눌러 새 ID가 생기는지, 이전 세션의 답변/점수/완료 시각이 유지되는지 확인한다.
6. 백엔드를 Ctrl+C로 종료하고 터미널 1의 명령으로 재실행한다. DB 파일을 삭제하지 않는다. 재로그인 후 기존 세션 URL 및 마이페이지에서 결과를 확인한다.
7. 다른 계정에서 그 세션을 조회·제출하면 404인지 확인한다. 사진/앨범 삭제와 퀴즈 초기화는 이력 삭제 동작이므로 보존 확인 중에는 사용하지 않는다.

배포에는 기존 DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET, 필요에 따라 CORS_ALLOWED_ORIGINS 및 R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_ENDPOINT/R2_BUCKET_NAME이 필요하다. 사진 분석/질문 생성은 기존 GEMINI_API_KEY, 위치 기능은 KAKAO_REST_API_KEY를 사용한다. 새 피드백 전용 비밀키는 없다. local 프로필의 공개 체험용 설정은 배포에 사용하지 않는다.

## 이번 작업에서 수정·추가한 파일

기존 작업 트리의 다른 변경은 이번 작업의 변경 목록에 포함하지 않는다.

| 파일(저장소 기준) | 변경 이유 |
|---|---|
| backend/src/main/java/com/konkuk/memory/entity/Answer.java | 선택 번호와 확정 채점 저장 |
| backend/src/main/java/com/konkuk/memory/entity/Result.java | 객관식 집계와 규칙 기반 피드백 버전 저장 |
| backend/src/main/java/com/konkuk/memory/entity/TrainingSession.java | 진행 중인 세션만 완료하도록 도메인 검증 |
| backend/src/main/java/com/konkuk/memory/dto/QuizSubmissionRequest.java | null 답변 항목 검증 |
| backend/src/main/java/com/konkuk/memory/dto/QuizSubmissionResponse.java | 기존 채점 응답에 저장된 세션 상세 추가 |
| backend/src/main/java/com/konkuk/memory/dto/TrainingSessionDetailResponse.java | 저장된 선택·정답·집계·피드백 버전 응답 |
| backend/src/main/java/com/konkuk/memory/dto/TrainingSessionSummaryResponse.java | 이력 목록 결과 집계 추가 |
| backend/src/main/java/com/konkuk/memory/service/QuizSubmissionService.java | 소유권 잠금·검증·답변/결과/완료 원자적 저장 |
| backend/src/main/java/com/konkuk/memory/service/QuizSummaryGenerator.java | 저장된 채점에 근거한 규칙 기반 피드백 |
| backend/src/main/java/com/konkuk/memory/service/RecallFeedbackService.java | 기존 주관식 피드백 유지 및 객관식 생성 분기 |
| backend/src/main/java/com/konkuk/memory/service/MyPageService.java | 페이지 내 결과를 일괄 조회해 목록에 연결 |
| backend/src/main/java/com/konkuk/memory/controller/UserController.java | 저장 커밋 후 별도 피드백 처리 및 저장 결과 반환 |
| backend/src/main/resources/db/migration/V11__persist_quiz_scores.sql | nullable 객관식 컬럼과 무결성 CHECK 추가 |
| backend/src/test/java/com/konkuk/memory/service/QuizSubmissionServiceTest.java | 기존 단위 테스트를 쓰기 서비스 구조에 맞게 보완 |
| backend/src/test/java/com/konkuk/memory/QuizPersistenceIntegrationTest.java | 영구 저장·동시성·롤백·실패·권한·이력 15개 검증 |
| backend/src/test/java/com/konkuk/memory/QuizBrowserTestServer.java | 실제 API 브라우저 테스트용 격리 DB fixture |
| frontend/src/pages/QuizPage.jsx | 저장 결과 복원·전체 문항 채점·실패 복구·재훈련·사진별 이력 |
| frontend/src/pages/RecallPage.jsx | 기존 주소에서 객관식/기존 주관식 화면 분기 |
| frontend/src/pages/MyPage.jsx | 목록/상세 점수·질문별 채점·같은 사진 필터 |
| frontend/playwright.quiz.config.js | Edge 및 격리된 테스트 서버 설정 |
| frontend/tests/quiz-backend.init.gradle | 본 서비스 빌드 설정을 바꾸지 않는 테스트 서버 실행 작업 |
| frontend/tests/quiz-persistence.spec.js | 새로고침·재로그인·재훈련·오류 복구 브라우저 테스트 |
| backend/docs/QUIZ_PERSISTENCE.md | 설계·API·DB·삭제 범위·검증·실행 절차 문서화 |

App.jsx, api/client.js, CSS는 기존 구현을 그대로 재사용했으며 이번 작업에서 추가 수정하지 않았다.
