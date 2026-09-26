import React from "react";
import { Badge, labels } from "../components/UI";
import {
  exampleSubmission,
  mockTraining,
  TRAINING_PREVIEW_KEY,
} from "../mocks/training";

function readResult() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(TRAINING_PREVIEW_KEY));
    if (saved?.sessionId === mockTraining.sessionId && saved.completedAt)
      return saved;
  } catch {
    // Use the sample result when the result page is opened directly.
  }
  return exampleSubmission();
}

export default function TrainingResultPage() {
  const result = readResult();
  const values = Object.values(result.answers);
  const counts = {
    ANSWERED: values.filter((item) => item.status === "ANSWERED").length,
    CANNOT_RECALL: values.filter((item) => item.status === "CANNOT_RECALL")
      .length,
    QUESTION_MISMATCH: values.filter(
      (item) => item.status === "QUESTION_MISMATCH",
    ).length,
  };
  const feedback =
    counts.ANSWERED === mockTraining.questions.length
      ? "모든 질문에 답하며 사진 속 기억을 구체적으로 되짚었습니다."
      : "떠오른 기억을 충분히 기록했습니다. 기억나지 않는 질문은 다음 훈련에서 다시 천천히 살펴볼 수 있습니다.";

  return (
    <div className="training-shell">
      <div className="page-title">
        <p className="eyebrow">03 / 기록</p>
        <h1>오늘의 기억을 기록했어요.</h1>
        <p className="muted">
          아래 결과와 피드백은 프론트엔드 테스트용 임시 결과입니다.
        </p>
      </div>

      <section className="result-summary card">
        <div>
          <span className="badge success">훈련 완료</span>
          <h2>{mockTraining.photo.originalFilename}</h2>
          <p className="muted">
            {mockTraining.photo.confirmedTakenOn} ·{" "}
            {mockTraining.photo.confirmedLocation}
          </p>
        </div>
        <div className="result-stat-grid">
          <div>
            <strong>{counts.ANSWERED}</strong>
            <span>답변 완료</span>
          </div>
          <div>
            <strong>{counts.CANNOT_RECALL}</strong>
            <span>기억나지 않음</span>
          </div>
          <div>
            <strong>{counts.QUESTION_MISMATCH}</strong>
            <span>질문 불일치</span>
          </div>
        </div>
      </section>

      <section className="card feedback-card">
        <p className="eyebrow">TRAINING FEEDBACK</p>
        <h2>오늘의 회상 피드백</h2>
        <p>{feedback}</p>
      </section>

      <section className="card result-answers">
        <div className="section-heading">
          <h2>질문과 답변</h2>
          <span className="muted">{mockTraining.questions.length}개 질문</span>
        </div>
        {mockTraining.questions.map((question) => {
          const answer = result.answers[question.questionId];
          return (
            <article className="question" key={question.questionId}>
              <p className="eyebrow">
                질문 {question.questionOrder} · {labels[question.type]}
              </p>
              <h3>{question.content}</h3>
              <div className="answer">
                <Badge value={answer.status} />
                <p>
                  {answer.status === "ANSWERED"
                    ? answer.content
                    : labels[answer.status]}
                </p>
              </div>
            </article>
          );
        })}
      </section>

      <div className="result-actions">
        <a
          className="button"
          href="#/training-demo"
          onClick={() => sessionStorage.removeItem(TRAINING_PREVIEW_KEY)}
        >
          다시 훈련하기
        </a>
        <a href="#/mypage">마이페이지로 이동</a>
        <a href="#/">홈으로 돌아가기</a>
      </div>
    </div>
  );
}
