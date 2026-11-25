# 7일 지연회상 평가

## 참여 흐름

1. 앨범을 먼저 생성하고 해당 앨범에 사진을 업로드합니다.
2. 새 퀴즈의 첫 문항 전에 ‘7일 지연회상 평가 선택’을 누른 경우에만 사진별 정보 입력을 표시합니다.
3. 사진마다 이미지 바로 아래의 한 줄 입력란에 실제 경험을 입력하고 직접 확인합니다. 한 사진당 500자, 최대 100장입니다.
4. 사진 정보 저장 후 훈련을 시작합니다. 일반 퀴즈만 진행하면 정보 입력과 평가 등록을 건너뜁니다.
5. 훈련 완료부터 168시간 뒤 사진·보기·기준 정보 없는 자유 서술 평가에 참여합니다.
6. 임시 저장과 최종 제출을 지원합니다. 최종 제출 후에는 답변을 수정할 수 없습니다.

## 저장과 검증

등록 요청: `POST /api/users/me/training-sessions/{id}/delayed-recall`

```json
{"facts":[{"photoId":1,"content":"부산 해변에서 친구와 산책했다."}],"confirmed":true}
```

로그인 사용자가 소유한 훈련과 같은 앨범에 속한 사진만 허용합니다. 사진 ID 중복, 내용 중복, 빈 내용, 여러 줄 입력을 거부합니다.
사진 ID와 기준 정보를 별도 필드로 저장하며 등록 후 기준을 고정합니다. 같은 요청 재전송은 기존 예약을 반환합니다.
사진이 삭제되어도 다른 사진에 대한 평가 기준은 보존되며, 해당 사진 참조만 null이 됩니다.
기존 평가 기록과 점수는 보존합니다. 과거 기준 정보의 사진 ID는 확실히 연결할 수 없으므로 null로 유지합니다.

## API

- GET /api/users/me/delayed-recalls: 내 평가 목록 (albumId 필터 선택)
- GET /api/users/me/training-sessions/{id}/delayed-recall: 훈련 예약 조회 또는 null
- POST /api/users/me/training-sessions/{id}/delayed-recall: 사진별 정보 등록
- GET /api/users/me/delayed-recalls/{id}: 내 평가 조회
- POST /api/users/me/delayed-recalls/{id}/start: 7일 경과 후 시작
- PATCH /api/users/me/delayed-recalls/{id}/draft: version, text, cannotRecall 저장
- POST /api/users/me/delayed-recalls/{id}/submit: 최종 제출

사람이 확인·채점하는 화면, API 및 계정 권한 설정은 제거했습니다. AI 자동 채점 연결은 다음 수정에서 진행합니다.
현재 새로 제출한 평가는 SUBMITTED 상태로 저장되며 자동 점수를 만들지 않습니다. 과거 GRADED 기록의 점수는 계속 표시합니다.

## 검증

백엔드는 실제 Flyway 마이그레이션, H2, Spring MVC와 인증으로 사진 연결·소유권·7일 경계·기준 비노출·삭제 연쇄를 검증합니다.
프런트엔드는 일반 퀴즈에서 입력란이 없는지, 선택 후 사진 바로 아래에 입력란이 표시되는지, 사진 ID와 정보가 함께 저장되는지 확인합니다.


## 훈련 이름

평가 제목도 원본 훈련과 같은 `displayName`을 사용합니다. 예: ‘부산 1의 회상 평가’.
앨범 이름은 표시하되 등록한 사진별 기준 정보는 채점 완료 전까지 계속 숨깁니다.
평가 가능 시각과 남은 일수의 계산 방식은 변경하지 않습니다.
