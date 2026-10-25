# 데이터베이스

DB 설계와 실행 자료를 찾기 위한 안내입니다.

| 자료 | 위치 |
| --- | --- |
| 테이블 설계·ERD | [DATABASE.md](../backend/docs/DATABASE.md) |
| 로컬 MySQL 연결 | [LOCAL_MYSQL.md](../backend/docs/LOCAL_MYSQL.md) |
| Flyway SQL | [db/migration](../backend/src/main/resources/db/migration) |
| JPA 엔티티 | [entity](../backend/src/main/java/com/konkuk/memory/entity) |
| 회상 기록 저장 | [RECALL.md](../backend/docs/RECALL.md) |
| 객관식 점수 저장 | [QUIZ_PERSISTENCE.md](../backend/docs/QUIZ_PERSISTENCE.md) |

기본 서버 설정은 MySQL, 로컬 체험 프로필은 H2 파일 DB를 사용합니다.

Flyway SQL의 관리 위치는 `backend/src/main/resources/db/migration/`입니다. 변경은 새 버전의 마이그레이션으로 추가하고, 적용된 SQL을 수정하거나 이 폴더에 복제하지 않습니다.

이 폴더에는 향후 설계 변경 설명, ERD 이미지, 익명화한 예제 데이터 안내를 추가합니다. 실제 DB 파일과 사용자 데이터는 커밋하지 않습니다.
