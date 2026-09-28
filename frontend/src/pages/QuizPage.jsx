import React, { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import PhotoImage from "../components/PhotoImage";

const DIFFICULTY_LABELS = {
  EASY: "쉬움",
  MEDIUM: "보통",
  HARD: "어려움",
};

function difficultyLabel(value) {
  return DIFFICULTY_LABELS[value] || "보통";
}

export default function QuizPage({ albumId, sessionId }) {
  const [album, setAlbum] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const [scoreResult, setScoreResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [submissionError, setSubmissionError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    async function loadQuiz() {
      try {
        setLoading(true);
        setError("");
        const [albumResponse, photoResponse, sessionResponse] =
          await Promise.all([
            api.album(albumId, controller.signal),
            api.albumPhotos(albumId, controller.signal),
            api.detail(sessionId, controller.signal),
          ]);

        const albumPhotoIds = new Set(photoResponse.map((photo) => photo.id));
        if (!albumPhotoIds.has(sessionResponse.photo?.id)) {
          throw new Error("이 앨범의 퀴즈가 아닙니다.");
        }

        if (!controller.signal.aborted) {
          setAlbum(albumResponse);
          setPhotos(photoResponse);
          setSession(sessionResponse);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || "퀴즈를 불러오지 못했습니다.");
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }

    loadQuiz();
    return () => controller.abort();
  }, [albumId, sessionId]);

  const questions = useMemo(
    () =>
      (session?.questions || [])
        .filter((question) => question.options?.length === 4)
        .sort((left, right) => left.questionOrder - right.questionOrder),
    [session],
  );

  const photoById = useMemo(
    () => new Map(photos.map((photo) => [photo.id, photo])),
    [photos],
  );

  if (loading) {
    return (
      <section className="card quiz-page">
        <p className="loading" role="status">퀴즈를 준비하는 중입니다…</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card quiz-page">
        <a className="back-link" href={`#/albums/${encodeURIComponent(albumId)}`}>
          ← 앨범으로 돌아가기
        </a>
        <p className="error" role="alert">{error}</p>
      </section>
    );
  }

  if (!questions.length) {
    return (
      <section className="card quiz-page">
        <a className="back-link" href={`#/albums/${encodeURIComponent(albumId)}`}>
          ← 앨범으로 돌아가기
        </a>
        <h1>새 퀴즈가 필요합니다</h1>
        <p className="muted">
          이 기록은 객관식 퀴즈를 지원하지 않는 이전 형식입니다. 앨범에서 새 질문을 생성해주세요.
        </p>
      </section>
    );
  }

  const answeredCount = Object.keys(answers).length;

  if (scoreResult) {
    const wrongAnswers = scoreResult.results.filter((item) => !item.correct);
    const percentage = Math.round(
      (scoreResult.correctCount / scoreResult.totalCount) * 100,
    );

    return (
      <section className="card quiz-page quiz-result-page">
        <p className="eyebrow">ALBUM RECALL RESULT</p>
        <h1>회상 퀴즈 결과</h1>
        <div className="quiz-score-summary">
          <strong>{scoreResult.correctCount} / {scoreResult.totalCount}</strong>
          <span>{percentage}점</span>
          <p>{scoreResult.incorrectCount}개 문제를 다시 확인해보세요.</p>
        </div>

        {wrongAnswers.length ? (
          <section className="wrong-answer-section">
            <h2>틀린 문제</h2>
            <div className="wrong-answer-list">
              {wrongAnswers.map((item, index) => (
                <article className="wrong-answer-card" key={item.questionId}>
                  <div className="wrong-answer-heading">
                    <span>오답 {index + 1}</span>
                    <span className={`difficulty-badge difficulty-${item.difficulty?.toLowerCase() || "medium"}`}>
                      {difficultyLabel(item.difficulty)}
                    </span>
                  </div>
                  <h3>{item.content}</h3>
                  <p className="selected-wrong-answer">
                    <span>내가 고른 답</span>
                    {item.selectedOption}
                  </p>
                  <p className="correct-answer">
                    <span>정답</span>
                    {item.correctOption}
                  </p>
                </article>
              ))}
            </div>
          </section>
        ) : (
          <p className="all-correct-message">모든 문제를 맞혔습니다.</p>
        )}

        <div className="quiz-complete-actions">
          <button
            className="button"
            type="button"
            onClick={() => {
              setAnswers({});
              setCurrentIndex(0);
              setScoreResult(null);
              setSubmissionError("");
              window.scrollTo(0, 0);
            }}
          >
            다시 풀기
          </button>
          <a className="button secondary-button" href={`#/albums/${encodeURIComponent(albumId)}`}>
            앨범으로 돌아가기
          </a>
        </div>
      </section>
    );
  }

  const question = questions[currentIndex];
  const selectedOption = answers[question.id];
  const displayPhotos = (question.displayPhotoIds || [])
    .map((photoId) => photoById.get(photoId))
    .filter(Boolean);
  const progress = ((currentIndex + 1) / questions.length) * 100;

  function selectOption(index) {
    setSubmissionError("");
    setAnswers((current) => ({ ...current, [question.id]: index }));
  }

  async function moveNext() {
    if (selectedOption === undefined || submitting) return;
    if (currentIndex !== questions.length - 1) {
      setCurrentIndex((current) => current + 1);
      window.scrollTo(0, 0);
      return;
    }

    try {
      setSubmitting(true);
      setSubmissionError("");
      const submittedAnswers = questions.map((item) => ({
        questionId: item.id,
        selectedOptionIndex: answers[item.id],
      }));
      const result = await api.submitQuiz(sessionId, submittedAnswers);
      setScoreResult(result);
      window.scrollTo(0, 0);
    } catch (err) {
      setSubmissionError(err.message || "퀴즈를 채점하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="card quiz-page">
      <div className="quiz-page-heading">
        <div>
          <a className="back-link" href={`#/albums/${encodeURIComponent(albumId)}`}>
            ← 퀴즈 나가기
          </a>
          <p className="eyebrow">{album?.title || "앨범"} RECALL QUIZ</p>
          <h1>앨범 회상 퀴즈</h1>
        </div>
        <strong aria-live="polite">{currentIndex + 1} / {questions.length}</strong>
      </div>

      <div
        className="quiz-progress"
        role="progressbar"
        aria-valuemin="1"
        aria-valuemax={questions.length}
        aria-valuenow={currentIndex + 1}
        aria-label="퀴즈 진행률"
      >
        <span style={{ width: `${progress}%` }} />
      </div>

      <article className="quiz-stage">
        {displayPhotos.length ? (
          <div>
            <p className="quiz-photo-label">기억 단서 사진</p>
            <div className={`quiz-photo-grid quiz-photo-count-${Math.min(displayPhotos.length, 4)}`}>
              {displayPhotos.map((photo) => (
                <PhotoImage
                  key={photo.id}
                  albumId={albumId}
                  photo={photo}
                  variant="quiz"
                />
              ))}
            </div>
          </div>
        ) : (
          <p className="quiz-hidden-photo-notice">사진 없이 당시의 기억을 떠올려보세요.</p>
        )}

        <div className="quiz-question-area">
          <div className="quiz-question-meta">
            <p className="quiz-question-number">질문 {currentIndex + 1}</p>
            <span className={`difficulty-badge difficulty-${question.difficulty?.toLowerCase() || "medium"}`}>
              {difficultyLabel(question.difficulty)}
            </span>
          </div>
          <h2>{question.content}</h2>
          <div className="quiz-choice-grid" role="radiogroup" aria-label="답 선택">
            {question.options.map((option, index) => {
              const selected = selectedOption === index;
              return (
                <button
                  key={`${question.id}-${index}`}
                  className={`quiz-choice${selected ? " selected" : ""}`}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => selectOption(index)}
                >
                  <span>{index + 1}</span>
                  {option}
                </button>
              );
            })}
          </div>
        </div>
      </article>

      {submissionError ? (
        <p className="error" role="alert">{submissionError}</p>
      ) : null}

      <div className="quiz-navigation">
        <button
          type="button"
          disabled={currentIndex === 0 || submitting}
          onClick={() => {
            setCurrentIndex((current) => current - 1);
            window.scrollTo(0, 0);
          }}
        >
          이전
        </button>
        <span>{answeredCount}개 선택함</span>
        <button
          className="button"
          type="button"
          disabled={selectedOption === undefined || submitting}
          onClick={moveNext}
        >
          {submitting
            ? "채점 중…"
            : currentIndex === questions.length - 1
              ? "제출하고 결과 보기"
              : "다음"}
        </button>
      </div>
    </section>
  );
}