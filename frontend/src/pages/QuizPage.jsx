import React, { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import PhotoImage from "../components/PhotoImage";

export default function QuizPage({ albumId, sessionId }) {
  const [album, setAlbum] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const [completed, setCompleted] = useState(false);

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

  if (completed) {
    return (
      <section className="card quiz-page quiz-complete">
        <p className="eyebrow">ALBUM RECALL COMPLETE</p>
        <h1>회상 퀴즈를 완료했습니다</h1>
        <p>
          {album?.title || "앨범"}의 기억 {questions.length}개를 모두 떠올려봤습니다.
        </p>
        <div className="quiz-complete-actions">
          <button
            className="button"
            type="button"
            onClick={() => {
              setAnswers({});
              setCurrentIndex(0);
              setCompleted(false);
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
    setAnswers((current) => ({ ...current, [question.id]: index }));
  }

  function moveNext() {
    if (selectedOption === undefined) return;
    if (currentIndex === questions.length - 1) {
      setCompleted(true);
      window.scrollTo(0, 0);
      return;
    }
    setCurrentIndex((current) => current + 1);
    window.scrollTo(0, 0);
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
          <p className="quiz-question-number">질문 {currentIndex + 1}</p>
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

      <div className="quiz-navigation">
        <button
          type="button"
          disabled={currentIndex === 0}
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
          disabled={selectedOption === undefined}
          onClick={moveNext}
        >
          {currentIndex === questions.length - 1 ? "퀴즈 완료" : "다음"}
        </button>
      </div>
    </section>
  );
}