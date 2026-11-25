# 기억산책 프론트엔드

2026-09-25 검증: 프론트 프로덕션 빌드 성공, 기존 백엔드 테스트 42개 통과,
Chromium 브라우저 테스트 7개 통과. 실제 Spring Boot + 격리된 H2로 API를 연결했으며
운영 MySQL/8080 서버에는 테스트 계정을 생성하지 않았습니다.
백엔드 결과: `../build/frontend-verification/reports/tests/test/index.html`.
브라우저 최종 결과: `test-results/.last-run.json` (Git 제외).

React + Vite 기반 SPA입니다. 백엔드 Controller/DTO/Service/SecurityConfig와
`docs/MY_PAGE.md`, `docs/LOGOUT.md`의 실제 계약을 사용합니다. 백엔드 코드는 변경하지 않습니다.

## 실행

Node.js **20.9 이상**이 필요합니다. 팀의 새 환경에는 Node.js 22.12 이상을 권장합니다.
현재 PC의 Node 20.9에서도 실행 가능한 Vite 6을 사용했습니다.
호환성: https://v6.vite.dev/guide/ . 설치 버전은 package-lock.json으로 고정됩니다.

프로젝트 루트에서 PowerShell:

```powershell
cd frontend
npm install
Copy-Item .env.example .env.local
npm run dev
```

브라우저에서 **http://localhost:5173**에 접속합니다. 이미 `.env.local`이 있다면 복사로 덮어쓰지 말고 편집합니다.
개발 서버는 포트가 사용 중이면 다른 포트로 자동 변경하지 않고 오류를 냅니다.

`.env.local`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

환경변수 변경 후 Vite를 다시 실행합니다. 주소는 API 경로 `/api`를 제외한 서버 기본 주소입니다.
Cloudflare/배포 주소를 사용할 때도 이 값만 해당 HTTPS 백엔드 주소로 변경합니다.
Vite 환경변수는 브라우저에 공개되므로 **DB_PASSWORD, JWT_SECRET, 실제 비밀번호를 넣지 않습니다.**
빌드 후에는 환경변수 변경 시 다시 빌드해야 합니다. 정적 결과물은 `dist/`입니다.

```powershell
npm run build
npm run preview
```

해시 라우팅(`/#/login`, `/#/mypage`)을 사용하므로 정적 호스팅에서 별도 경로 rewrite 없이 새로고침할 수 있습니다.

## 백엔드

Java 17, MySQL과 기존 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`이 필요합니다.
DB/JWT 설정은 기존 [백엔드 실행 안내](../docs/LOCAL_MYSQL.md)의 '평소 서버 실행 명령'을 참고합니다.
**이미 실행 중인 8080 서버가 있다면 중복 실행하거나 종료할 필요가 없습니다.**
새 서버 실행이 필요한 경우 환경변수를 설정한 별도 PowerShell의 프로젝트 루트에서:

```powershell
.\gradlew.bat --gradle-user-home .gradle-home --no-daemon bootRun
```

이 명령은 설정된 MySQL에 연결하며, 아직 적용되지 않은 Flyway 마이그레이션을 적용합니다.
최신 로그아웃에는 `V2__create_revoked_tokens.sql`이 필요합니다. 구버전 실행 서버는 소스 수정만으로 갱신되지 않습니다.
실제 DB 검증 스크립트 `Start-LocalMySqlVerification.ps1`은 프론트 실행에 필요하지 않습니다.

기본 백엔드 CORS는 `http://localhost:5173`을 이미 허용하므로 변경하지 않았습니다.
`http://127.0.0.1:5173`은 다른 Origin이므로 기본 접속 주소는 localhost를 사용합니다.
외부 프론트 도메인 사용 시 백엔드의 `CORS_ALLOWED_ORIGINS`에 **프론트 Origin**을 명시하고 백엔드에 반영해야 합니다.
예: `https://my-frontend.example` (경로와 끝 슬래시 제외). 와일드카드는 사용하지 않습니다.
HTTPS 프론트에서는 HTTPS API를 사용해야 브라우저의 혼합 콘텐츠 차단을 피할 수 있습니다.

## 실제 연결 API

| 요청                                              | 요청 본문 / 응답                                                    |
| ------------------------------------------------- | ------------------------------------------------------------------- |
| POST /api/auth/signup                             | email, password, name → id, email, name (201)                       |
| POST /api/auth/login                              | email, password → accessToken, tokenType, expiresIn                 |
| POST /api/auth/logout                             | 본문 없음, Bearer 토큰 → 204                                        |
| GET /api/users/me                                 | id, name, email, createdAt, updatedAt                               |
| GET /api/users/me/photos?page=0&size=6            | content, page, size, totalElements, totalPages, hasNext             |
| GET /api/users/me/training-sessions?page=0&size=6 | 같은 페이지 구조, 사진 기본정보·상태·시작/완료일                    |
| GET /api/users/me/training-sessions/{sessionId}   | photo, status, startedAt, completedAt, questions(각 answer), result |

이메일은 필수·254자 이내, 가입 이름은 필수·100자 이내, 가입 비밀번호는 8~72자이며
로그인 비밀번호는 필수·72자 이내입니다. 양쪽 모두 UTF-8 72바이트 제한을 검사합니다.
서버의 fieldErrors와 message를 표시하며 최종 검증은 백엔드가 수행합니다.

## 화면과 데이터 범위

- 메인, 회원가입, 로그인, 마이페이지.
- 마이페이지: 회원정보, 페이지별 사진 정보, 훈련 목록·상세, 질문/답변/결과, 빈 상태·오류·재시도.
- 최근 활동: 사진 첫 페이지와 훈련 첫 페이지에서 최신 6건을 합쳐 정렬한 목록입니다.
  사진 등록일과 훈련 시작일만 사용하며 전체 활동 로그나 마지막 로그인 시간은 아닙니다.
- 사진 원본/썸네일 API가 없어 파일명·용량·촬영일·장소 등 메타데이터만 표시합니다.
- 사진 업로드, 훈련 생성, 답변 제출, 회원정보 수정 등 없는 API는 호출하지 않습니다.
- 일반 실행 화면에는 가짜 데이터가 없습니다. 테스트용 데이터는 격리된 H2 실행기에만 있습니다.

## 인증 방식

- JWT와 만료시간을 **sessionStorage**에 저장합니다. 같은 탭의 새로고침은 유지되며 탭을 닫으면 삭제됩니다.
  브라우저 재실행까지 유지하는 '자동 로그인'은 구현하지 않았습니다.
- 토큰이 없으면 보호된 페이지를 렌더링하지 않고 로그인으로 이동합니다.
- API 공통 모듈이 Bearer 헤더를 첨부합니다. 만료 타이머 및 인증 API의 401에서 토큰을 정리합니다.
  로그인 실패의 401은 로그인 폼 오류로 표시합니다.
- 로그아웃은 백엔드 204를 받은 후 로컬 토큰을 지웁니다. 네트워크/서버 실패 시 토큰을 유지하고 재시도 안내를 표시합니다.
  이미 무효한 토큰의 401은 로컬 인증도 정리합니다.
- JWT 원문/비밀번호를 로그에 출력하지 않습니다. 브라우저 JS 저장소이므로 XSS 예방을 위해 사용자 값을 React 텍스트로만 렌더링합니다.
- 프론트 보호는 화면 처리이며 데이터 소유권은 백엔드가 JWT 사용자 기준으로 검사합니다.

## 테스트

백엔드 테스트와 브라우저 테스트는 실제 MySQL/8080 서버를 사용하지 않습니다.

프로젝트 루트에서 기존 백엔드 전체 검증:

```powershell
.\gradlew.bat --gradle-user-home .gradle-home --no-daemon --offline --init-script frontend/tests/backend.init.gradle build
```

프론트 폴더에서:

```powershell
npx playwright install chromium
npm run test:e2e
```

Playwright가 테스트 전용 Spring Boot **127.0.0.1:18080 / H2**와 Vite **localhost:5173**을 시작하고 종료합니다.
테스트 전용 포트가 사용 중이면 재사용하지 않고 실패합니다. 프론트 개발 서버를 실행 중이라면 종료 후 테스트합니다.
Gradle 캐시에 의존성이 없는 새 환경은 먼저 `--offline`을 빼고 위 백엔드 빌드를 한 번 실행합니다.
테스트 실행기는 `frontend/tests/backend.init.gradle`에서만 test 소스에 추가되며 운영 JAR에 포함되지 않습니다.

브라우저 테스트는 실제 API를 사용해 가입/로그인/빈 목록/채워진 목록/페이지네이션/상세/로그아웃/401을 검증합니다.
네트워크 실패와 상세 404 화면 검사는 브라우저의 응답 가로채기로 별도 검증합니다.
프론트 만료 타이머 테스트는 저장된 만료시간을 단축하며, JWT 자체의 만료 거부는 기존 백엔드 테스트로 검증합니다.
운영 MySQL의 실제 계정·사진 기록 표시는 사용자가 최종 확인해야 합니다.

## 폴더

```text
frontend/
  src/api/client.js            # 공통 요청·인증 저장소
  src/components/UI.jsx       # 상태, 오류, 날짜, 페이지 이동
  src/pages/Home.jsx
  src/pages/AuthPage.jsx       # 가입/로그인 폼
  src/pages/MyPage.jsx         # 회원·사진·훈련·상세·최근 활동
  src/App.jsx                 # 경로·메뉴·보호 화면·로그아웃
  src/main.jsx
  src/styles.css
  tests/app.spec.js
  tests/backend/FrontendTestServer.java
  tests/backend.init.gradle
  playwright.config.js
  index.html
  .env.example
  package.json
  package-lock.json
```



### 앨범 생성 → 사진 추가

사진 보관함에서 경험·여행별 앨범을 먼저 생성합니다. 생성 직후 해당 앨범으로 이동하며,
‘+ 사진 추가’로 사진을 업로드하면 선택한 앨범에만 저장됩니다. 같은 지역의 다른 여행은
별도 앨범으로 유지됩니다. 앨범 안에서 퀴즈를 생성하고 해당 앨범의 7일 지연회상 평가 일정을 확인할 수 있습니다.
평가 참여 등록은 기존처럼 퀴즈의 첫 문항 전에 진행합니다.
7일 평가를 선택하면 사진별 정보 입력을 표시합니다. AI 채점은 별도 수정입니다.

앨범 업로드 흐름 브라우저 테스트: `npx playwright test --config playwright.album.config.js`



### 훈련 이름

모든 훈련은 서버의 `displayName`을 사용해 ‘부산 1’, ‘부산 2’처럼 표시합니다.
순번은 앨범별 생성 순서이며, 사진이 여러 장이어도 같은 앨범의 훈련은 같은 번호 흐름을 사용합니다.
기존 기록에도 생성 순서로 번호를 부여하고, 삭제한 번호는 다시 사용하지 않습니다.
앨범 이름을 변경하면 훈련 이름의 앨범 부분도 함께 바뀝니다. 내부 ID와 기존 링크는 유지합니다.
