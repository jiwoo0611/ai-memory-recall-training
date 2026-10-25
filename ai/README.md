# AI · 질문 생성

사진 분석, 질문 생성, 프롬프트와 평가 자료를 정리하는 공간입니다.

## 현재 구현

AI 연동은 백엔드 서비스 안에서 실행됩니다.

- [사진 분석 클라이언트](../backend/src/main/java/com/konkuk/memory/service/GeminiPhotoAnalysisClient.java)
- [질문 생성 클라이언트](../backend/src/main/java/com/konkuk/memory/service/GeminiQuestionGenerationClient.java)
- [앨범 질문 생성 서비스](../backend/src/main/java/com/konkuk/memory/service/AlbumQuestionGenerationService.java)
- [환경변수 안내](../backend/.env.example)

## 자료 추가 기준

프롬프트 변경 이유, 사용 모델, 익명화한 입력·출력 예시, 질문의 근거와 관련성 평가를 이 폴더에 기록합니다. 실제 개인 사진과 API 키는 올리지 않습니다.

답변 저장·훈련 결과와 관련된 흐름은 [회상 훈련](../backend/docs/RECALL.md)과 [객관식 결과 저장](../backend/docs/QUIZ_PERSISTENCE.md)을 참고하세요.
