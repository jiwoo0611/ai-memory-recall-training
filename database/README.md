# DB 구성과 훈련 기록 저장

기본 서버는 MySQL과 JPA를 사용하며 Flyway로 스키마 변경 이력을 관리합니다. 로컬 체험은 H2 파일 DB를 사용합니다. 사진 파일은 DB에 직접 넣지 않고 R2 또는 로컬 파일로 저장합니다.

## 핵심 데이터 관계

| 데이터 | 역할 |
| --- | --- |
| users | 회원 정보와 인증 기준 |
| albums | 사용자별 사진 묶음 |
| photos | 사진 소유자, 저장소 키, 촬영 정보, AI 단서 |
| training_sessions | 훈련 상태와 시작·완료 시각 |
| questions | 세션별 질문, 보기, 정답, 근거 스냅샷 |
| answers | 문항별 사용자 답변, 선택 번호, 채점 결과 |
| results | 세션별 정답·오답 수와 피드백 상태 |

사용자에게 앨범과 사진이 연결되고, 사진을 기준으로 여러 훈련 세션을 만들 수 있습니다. 세션에는 질문과 결과가 연결되며 각 질문에는 최대 하나의 답변이 연결됩니다.

관계의 기본 설명은 [DATABASE.md](../backend/docs/DATABASE.md), 현재 스키마의 기준은 [Flyway SQL](../backend/src/main/resources/db/migration)과 [JPA 엔티티](../backend/src/main/java/com/konkuk/memory/entity)입니다. 기존 문서에 남은 과거 구현 상태는 현재 코드와 함께 확인합니다.

## 답변 제출 시 저장 순서

[QuizSubmissionService](../backend/src/main/java/com/konkuk/memory/service/QuizSubmissionService.java)는 다음을 하나의 쓰기 트랜잭션으로 처리합니다.

1. 로그인 사용자 소유의 세션을 잠금 조회합니다.
2. 완료 여부, 질문 ID·누락·중복, 보기 인덱스 등을 검증하고 채점합니다.
3. 문항별 답변을 저장합니다.
4. 세션 결과를 저장하고 세션을 완료 상태로 변경합니다.

저장 중 오류가 발생하면 해당 트랜잭션을 롤백합니다. 피드백 생성은 답변 저장과 분리하여, 피드백 오류로 답변까지 잃지 않도록 처리합니다.

## 다시 풀기와 기록 보존

재훈련은 기존 세션의 답변을 덮어쓰지 않고 새 세션과 질문 스냅샷을 만듭니다. 같은 요청 ID의 재시도는 같은 신규 세션을 반환합니다.

마이페이지는 저장된 훈련과 결과를 사용자 소유권 기준으로 조회합니다. 사진·앨범 삭제나 별도 퀴즈 초기화는 관련 이력을 삭제하므로 재훈련과 구분합니다.

## 스키마 변경과 검증

- SQL 버전 관리: [db/migration](../backend/src/main/resources/db/migration)
- 객관식 선택·점수 컬럼: [V11__persist_quiz_scores.sql](../backend/src/main/resources/db/migration/V11__persist_quiz_scores.sql)
- 저장·동시성·권한·롤백 검증: [QuizPersistenceIntegrationTest](../backend/src/test/java/com/konkuk/memory/QuizPersistenceIntegrationTest.java)
- API·설계·기존 검증 기록: [QUIZ_PERSISTENCE.md](../backend/docs/QUIZ_PERSISTENCE.md)
- MySQL 연결 안내: [LOCAL_MYSQL.md](../backend/docs/LOCAL_MYSQL.md)

기존 검증 문서는 H2와 브라우저 테스트 기록입니다. 최신 main 재실행과 실제 MySQL·R2 환경의 반복 저장·조회 검증은 별도로 수행해야 합니다.
