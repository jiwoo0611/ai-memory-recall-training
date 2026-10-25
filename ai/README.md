# AI 구성과 질문 생성

사진 분석과 질문 생성은 Spring Boot 백엔드의 서비스 계층에서 실행합니다. 이 폴더는 처리 과정과 구현 위치를 설명합니다.

## 1. 사진에서 회상 단서 추출

[GeminiPhotoAnalysisClient](../backend/src/main/java/com/konkuk/memory/service/GeminiPhotoAnalysisClient.java)가 이미지와 프롬프트를 Gemini에 전달하고 JSON 응답을 받습니다.

분석 결과에는 장면 요약, 활동, 음식·랜드마크와 신뢰도, 질문 단서, 불확실성, 질문 적합도 정보가 포함됩니다. 사진에 보이지 않는 사실과 사람의 신원·관계 등을 추측하지 않도록 프롬프트에서 제한합니다.

[PhotoAnalysisPersistenceService](../backend/src/main/java/com/konkuk/memory/service/PhotoAnalysisPersistenceService.java)가 분석 결과를 사진의 `aiClues`에 저장합니다. 프롬프트 제한은 오류 가능성을 줄이기 위한 설계이며 정확성을 보장하는 성능 측정 결과는 아닙니다.

## 2. 앨범의 질문 생성 준비 검사

[AlbumPhotoAnalysisService](../backend/src/main/java/com/konkuk/memory/service/AlbumPhotoAnalysisService.java)는 앨범 소유권, 사진 존재, 전체 사진 분석 완료 여부를 검사합니다.

[AlbumQuestionReadinessService](../backend/src/main/java/com/konkuk/memory/service/AlbumQuestionReadinessService.java)의 현재 코드 기준은 다음과 같습니다.

| 조건 | 설정값 |
| --- | --- |
| 사진 질문 적합도 | 60점 이상 |
| 서로 다른 대표 장면 | 6개 이상 |
| 주요 주제 종류 | 2종류 이상 |

비슷한 장면은 대표 사진을 선택하고, 근거가 부족하면 필요한 사진을 추가하도록 안내합니다. 적합도 점수는 AI 분석값과 코드 규칙으로 판단하는 내부 지표입니다.

## 3. 출처가 있는 앨범 근거 구성

[AlbumQuestionContextService](../backend/src/main/java/com/konkuk/memory/service/AlbumQuestionContextService.java)는 사진 ID, 촬영 시각·위치, 정제한 AI 단서를 하나의 JSON으로 묶습니다.

촬영 정보는 `EXIF`, AI 추정 정보는 `AI_INFERRED`로 구분합니다. 앨범 제목만으로 사진에 없는 사실을 생성하지 않도록 제한합니다.

## 4. 질문 후보 생성과 선별

[AlbumQuestionGenerationService](../backend/src/main/java/com/konkuk/memory/service/AlbumQuestionGenerationService.java)가 전체 과정을 조정합니다.

- 위치·시간 순서 질문은 근거가 있을 때 코드 기반 팩토리에서 생성합니다.
- 나머지는 [GeminiQuestionGenerationClient](../backend/src/main/java/com/konkuk/memory/service/GeminiQuestionGenerationClient.java)가 후보를 생성합니다.
- 현재 앨범 퀴즈는 총 **10문항**, 난이도는 **쉬움 5 · 보통 4 · 어려움 1**로 계획합니다.
- 보기 4개, 정답 인덱스, 근거 사진 ID, 중복 질문·정답, 질문 속 정답 노출 등을 검증하고 후보를 선별합니다.
- 어려운 질문에는 서로 다른 사진 2장 이상의 근거를 요구합니다.

위 수치는 현재 구현의 구성값이며 질문 품질·정확도 측정 결과가 아닙니다.

## 5. 질문과 근거 스냅샷 저장

[QuestionGenerationPersistenceService](../backend/src/main/java/com/konkuk/memory/service/QuestionGenerationPersistenceService.java)가 세션과 질문, 선택지, 정답, 근거를 저장하고 실패 상태를 처리합니다.

답안 채점과 객관식 결과 피드백은 저장된 정답·선택에 기초한 별도 처리입니다. 사진 분석·질문 생성과 같은 AI 호출로 혼동하지 않습니다. [결과 저장 문서](../backend/docs/QUIZ_PERSISTENCE.md)를 참고하세요.

## 설정과 검증 범위

- 키·모델 설정: [환경변수 예시](../backend/.env.example)
- 관련 테스트: [service 테스트](../backend/src/test/java/com/konkuk/memory/service)
- 추가 측정: 실제 AI 호출 시간, 유효 질문 반환 성공률, 사진 근거와 정답의 적절성

개인 사진 원본과 API 키는 이 폴더에 커밋하지 않습니다.
