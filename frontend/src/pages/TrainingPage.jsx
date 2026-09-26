import React, { useMemo, useState } from "react";
import {
  mockTraining,
  TRAINING_PREVIEW_KEY,
} from "../mocks/training";
import { Badge, labels } from "../components/UI";

function readDraft() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(TRAINING_PREVIEW_KEY));
    return saved?.sessionId === mockTraining.sessionId ? saved.answers || {} : {};
  } catch {
    return {};
  }
}

export default function TrainingPage() {
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState(readDraft);
  const [error, setError] = useState("");
  const question = mockTraining.questions[index];
  const answer = answers[question.questionId] || {
    status: "ANSWERED",
    content: "",
  };
  const progress = ((index + 1) / mockTraining.questions.length) * 100;

  const completedCount = useMemo(
    () =>
      mockTraining.questions.filter((item) => {
        const value = answers[item.questionId];
        return (
          value &&
          (value.status !== "ANSWERED" || Boolean(value.content?.trim()))
        );
      }).length,
    [answers],
  );

  function update(next) {
    const updated = { ...answers, [question.questionId]: next };
    setAnswers(updated);
    setError("");
    sessionStorage.setItem(
      TRAINING_PREVIEW_KEY,
      JSON.stringify({
        sessionId: mockTraining.sessionId,
        answers: updated,
      }),
    );
  }

  function canContinue() {
    if (answer.status === "ANSWERED" && !answer.content.trim()) {
      setError("답변을 입력하거나 다른 응답 방식을 선택해주세요.");
      return false;
    }
    return true;
  }

  function next() {
    if (!canContinue()) return;
    setIndex((current) =>
      Math.min(current + 1, mockTraining.questions.length - 1),
    );
  }

  function submit() {
    if (!canContinue()) return;
    const missing = mockTraining.questions.find((item) => {
      const value = answers[item.questionId];
      return (
        !value ||
        (value.status === "ANSWERED" && !value.content?.trim())
      );
    });
    if (missing) {
      setIndex(mockTraining.questions.indexOf(missing));
      setError("모든 질문에 답하거나 응답 방식을 선택해주세요.");
      return;
    }
    sessionStorage.setItem(
      TRAINING_PREVIEW_KEY,
      JSON.stringify({
        sessionId: mockTraining.sessionId,
        completedAt: new Date().toISOString(),
        answers,
      }),
    );
    location.hash = "/training-result-demo";
  }

  return (
    <div className="training-shell">
      <div className="page-title">
        <p className="eyebrow">02 / 회상</p>
        <h1>사진 속 이야기를 천천히 떠올려보세요.</h1>
        <p className="muted">
          현재 화면은 프론트엔드 미리보기이며 입력 내용은 이 브라우저에만
          임시 저장됩니다.
        </p>
      </div>

      <section className="training-photo card">
        <div className="file-mark" aria-hidden="true">▧</div>
        <div>
          <p className="eyebrow">선택한 사진</p>
          <h2>{mockTraining.photo.originalFilename}</h2>
          <p className="muted">
            {mockTraining.photo.confirmedTakenOn} ·{" "}
            {mockTraining.photo.confirmedLocation}
          </p>
        </div>
        <Badge value={mockTraining.status} />
      </section>

      <section className="card training-card">
        <div className="training-progress-heading">
          <div>
            <p className="eyebrow">
              QUESTION {index + 1} / {mockTraining.questions.length}
            </p>
            <span className="badge">{labels[question.type]}</span>
          </div>
          <span className="muted">{completedCount}개 응답 완료</span>
        </div>
        <div
          className="training-progress"
          role="progressbar"
          aria-label="훈련 진행률"
          aria-valuemin="0"
          aria-valuemax="100"
          aria-valuenow={Math.round(progress)}
        >
          <span style={{ width: `${progress}%` }} />
        </div>

        <h2 className="training-question">{question.content}</h2>

        <label htmlFor="recall-answer">나의 답변</label>
        <textarea
          id="recall-answer"
          rows="6"
          value={answer.status === "ANSWERED" ? answer.content : ""}
          disabled={answer.status !== "ANSWERED"}
          placeholder="사진을 보며 떠오르는 기억을 자유롭게 작성해주세요."
          onChange={(event) =>
            update({ status: "ANSWERED", content: event.target.value })
          }
        />

        <div className="answer-options" aria-label="응답 방식">
          <button
            type="button"
            className={answer.status === "ANSWERED" ? "selected" : ""}
            aria-pressed={answer.status === "ANSWERED"}
            onClick={() => update({ status: "ANSWERED", content: "" })}
          >
            직접 답변
          </button>
          <button
            type="button"
            className={answer.status === "CANNOT_RECALL" ? "selected" : ""}
            aria-pressed={answer.status === "CANNOT_RECALL"}
            onClick={() => update({ status: "CANNOT_RECALL", content: "" })}
          >
            기억나지 않음
          </button>
          <button
            type="button"
            className={answer.status === "QUESTION_MISMATCH" ? "selected" : ""}
            aria-pressed={answer.status === "QUESTION_MISMATCH"}
            onClick={() =>
              update({ status: "QUESTION_MISMATCH", content: "" })
            }
          >
            질문이 맞지 않음
          </button>
        </div>

        {error && <p className="training-error" role="alert">{error}</p>}

        <div className="training-actions">
          <button
            type="button"
            disabled={index === 0}
            onClick={() => {
              setError("");
              setIndex((current) => Math.max(0, current - 1));
            }}
          >
            이전 질문
          </button>
          {index < mockTraining.questions.length - 1 ? (
            <button type="button" className="button" onClick={next}>
              다음 질문
            </button>
          ) : (
            <button type="button" className="button" onClick={submit}>
              답변 제출하기
            </button>
          )}
        </div>
      </section>
    </div>
  );
}
