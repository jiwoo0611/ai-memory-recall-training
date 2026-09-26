import React from "react";
export const date = (value) =>
  value
    ? new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
export const labels = {
  CREATED: "훈련 준비",
  GENERATING_QUESTIONS: "질문 생성 중",
  IN_PROGRESS: "훈련 진행 중",
  COMPLETED: "완료",
  QUESTION_GENERATION_FAILED: "질문 생성 실패",
  PENDING: "대기 중",
  PROCESSING: "처리 중",
  FAILED: "실패",
  ANSWERED: "답변 완료",
  CANNOT_RECALL: "기억나지 않음",
  QUESTION_MISMATCH: "질문이 맞지 않음",
  PHOTO_CLUE: "사진 단서",
  EXPERIENCE: "경험",
  CONTEXT: "맥락",
  INFORMATION_CHECK: "정보 확인",
};
export function Badge({ value }) {
  return (
    <span className={`badge ${value === "COMPLETED" ? "success" : ""}`}>
      {labels[value] || value}
    </span>
  );
}
export function ErrorBox({ error, retry }) {
  return error ? (
    <div className="error" role="alert">
      <p>{error.message || error}</p>
      {retry && (
        <button className="text-button" onClick={retry}>
          다시 시도
        </button>
      )}
    </div>
  ) : null;
}
export function Empty({ children = "아직 기록이 없습니다." }) {
  return (
    <div className="empty">
      <span aria-hidden="true">○</span>
      <p>{children}</p>
    </div>
  );
}
export function Pager({ data, onPage }) {
  return data && data.totalPages > 1 ? (
    <nav className="pager" aria-label="목록 페이지">
      <button disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>
        이전
      </button>
      <span>
        {data.page + 1} / {data.totalPages}
      </span>
      <button disabled={!data.hasNext} onClick={() => onPage(data.page + 1)}>
        다음
      </button>
    </nav>
  ) : null;
}
