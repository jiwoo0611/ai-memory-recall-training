# 프로젝트 문서

## 구현·실행 문서

| 문서 | 내용 |
| --- | --- |
| [전체 소개](../README.md) | 기능, 기술, 저장소 구조, 빠른 시작 |
| [백엔드](../backend/README.md) | 서버 설정과 인증 API |
| [프론트엔드](../frontend/README.md) | 화면, 인증 처리와 브라우저 테스트 |
| [DB 설계](../backend/docs/DATABASE.md) | 테이블과 관계 |
| [MySQL 설정](../backend/docs/LOCAL_MYSQL.md) | 로컬 DB 연결·검증 |
| [로그아웃](../backend/docs/LOGOUT.md) | 토큰 무효화 |
| [마이페이지](../backend/docs/MY_PAGE.md) | 회원·훈련 조회 API |
| [회상 훈련](../backend/docs/RECALL.md) | 답변·이력·재훈련 |
| [객관식 결과](../backend/docs/QUIZ_PERSISTENCE.md) | 채점과 영구 저장 |
| [AI](../ai/README.md) | 분석·질문 생성 구현 위치 |
| [시연](../demo/README.md) | 로컬 체험과 시연 순서 |

기존 상세 문서는 `backend/docs/`에 유지합니다. 문서의 과거 작업 브랜치·검증 날짜·테스트 건수는 해당 작업 시점의 기록이며, 현재 실행 설정은 소스와 빌드 파일을 함께 확인합니다.

## 분야별 자료 추가

공통 문서가 준비되면 이 폴더 아래에 다음과 같이 분류하고 위 목차에 연결합니다.

- `architecture/`: 시스템 구조와 데이터 흐름
- `api/`: 공통 API 명세와 연동 규칙
- `design/`: 화면 흐름과 설계 결정
- `evaluation/`: 평가 방법과 실제 측정 결과
- `presentation/`: 제안서와 최종 발표 자료

현재는 문서 목차를 제공하며, 자료가 생길 때 해당 하위 폴더를 추가합니다.
