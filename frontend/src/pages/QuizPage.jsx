import React, { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api/client";
import PhotoImage from "../components/PhotoImage";
import { RecallEnrollment } from "../components/DelayedRecallSection";
import { date, Badge, ErrorBox, Pager } from "../components/UI";

const feedbackLabels = { PENDING: "생성 대기", PROCESSING: "생성 중", COMPLETED: "생성 완료", FAILED: "생성 실패" };
function savedScore(session) {
  if (session.status !== "COMPLETED" || session.result?.correctCount == null) return null;
  return { ...session.result, sessionId: session.id, results: session.questions.map(q => ({
    questionId: q.id, content: q.content, difficulty: q.difficulty,
    correct: q.answer?.correct, selectedOptionIndex: q.answer?.selectedOptionIndex,
    selectedOption: q.answer?.content, correctOption: q.answer?.correctOption,
    correctOptionIndex: q.answer?.correctOptionIndex,
  })) };
}

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
  const [revision, setRevision] = useState(0);
  const [history, setHistory] = useState(null);
  const [historyError, setHistoryError] = useState(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyRevision, setHistoryRevision] = useState(0);
  const actionLock = useRef(false);
  const retryKey = useRef(null);
  const [recallReady, setRecallReady] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    async function loadQuiz() {
      try {
        setLoading(true);
        setError("");
        const sessionResponse = await api.detail(sessionId, controller.signal);
        if (albumId && String(sessionResponse.photo?.albumId) !== String(albumId)) {
          throw new Error("이 앨범의 퀴즈가 아닙니다.");
        }
        // A stored result remains readable even if an album/photo request fails.
        const [albumResponse, photoResponse] = sessionResponse.status === "COMPLETED" || !albumId
          ? [null, []] : await Promise.all([api.album(albumId, controller.signal), api.albumPhotos(albumId, controller.signal)]);

        if (!controller.signal.aborted) {
          setAlbum(albumResponse);
          setPhotos(photoResponse);
          setSession(sessionResponse);
          setScoreResult(savedScore(sessionResponse));
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
  }, [albumId, sessionId, revision]);

  useEffect(() => {
    if (!session?.photo?.id) return;
    const controller = new AbortController();
    setHistory(null); setHistoryError(null);
    api.photoSessions(session.photo.id, historyPage, controller.signal)
      .then(value => { if (!controller.signal.aborted) setHistory(value); })
      .catch(error => { if (!controller.signal.aborted) setHistoryError(error); });
    return () => controller.abort();
  }, [session?.photo?.id, historyPage, historyRevision]);

  async function retrain() {
    if (actionLock.current) return;
    actionLock.current = true; setSubmitting(true); setSubmissionError("");
    retryKey.current ||= crypto.randomUUID();
    try {
      const next = await api.retrain(sessionId, retryKey.current);
      location.hash = `/training/${next.sessionId}`;
    } catch (error) { setSubmissionError(error.message); }
    finally { actionLock.current = false; setSubmitting(false); }
  }

  async function generateFeedback() {
    if (actionLock.current) return;
    actionLock.current = true; setSubmitting(true); setSubmissionError("");
    try {
      const saved = await api.feedback(sessionId);
      setSession(saved); setScoreResult(savedScore(saved));
    } catch (error) { setSubmissionError(`답변은 저장되어 있습니다. ${error.message}`); }
    finally { actionLock.current = false; setSubmitting(false); }
  }

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
        <button onClick={() => setRevision(v => v + 1)}>다시 시도</button>
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
    const questionResults = scoreResult.results;
    const reviewQuestions = session.questions.filter(q => q.answer?.correct === false);
    function showQuestion(id) {
      const target = document.getElementById(`quiz-question-${id}`);
      target?.focus({ preventScroll: true });
      target?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth", block: "start" });
    }
    const percentage = Math.round(
      (scoreResult.correctCount / scoreResult.totalCount) * 100,
    );

    return (
      <section className="card quiz-page quiz-result-page">
        <p className="eyebrow">ALBUM RECALL RESULT</p>
        <h1>회상 퀴즈 결과</h1>
        <p>{session.photo.originalFilename} · <Badge value={session.status} /></p>
        <p className="muted">시작 {date(session.startedAt)} · 완료 {date(session.completedAt)}</p>
        <div className="quiz-result-hero">
          <div className="quiz-result-stars" aria-label={`${Math.max(1, Math.round(scoreResult.correctCount / scoreResult.totalCount * 3))}개 별 달성`}>
            {[0, 1, 2].map(star => <span key={star} className={star < Math.round(scoreResult.correctCount / scoreResult.totalCount * 3) ? "earned" : ""}>★</span>)}
          </div>
          <p className="quiz-result-celebration">오늘의 퀴즈를 완주했어요!</p>
          <div className="quiz-score-summary">
          <strong>{scoreResult.correctCount} / {scoreResult.totalCount}</strong>
          <span>{percentage}점</span>
          <p>{scoreResult.totalCount}문항에 모두 답했고 {scoreResult.correctCount}문항을 맞혔어요.</p>
          </div>
          <div className="quiz-result-reward"><strong>+{scoreResult.correctCount * 10} XP</strong><span>다음 레벨을 향해 한 걸음 더!</span></div>
        </div>

        {questionResults.length ? (
          <details className="wrong-answer-section quiz-review-details">
            <summary>다시 확인할 문항 {scoreResult.incorrectCount}개</summary>
            <div className="wrong-answer-list">
              {questionResults.map((item, index) => (
                <article className="wrong-answer-card quiz-review-target" key={item.questionId}
                  id={`quiz-question-${item.questionId}`} tabIndex={-1} aria-label={`질문 ${index + 1} 상세`}>
                  <div className="wrong-answer-heading">
                    <span>질문 {index + 1} · {item.correct ? "정답" : "오답"}</span>
                    <span className={`difficulty-badge difficulty-${item.difficulty?.toLowerCase() || "medium"}`}>
                      {difficultyLabel(item.difficulty)}
                    </span>
                  </div>
                  <h3>{item.content}</h3>
                  <ol>{session.questions.find(q => q.id === item.questionId)?.options.map((option, i) => <li key={i}>{option}</li>)}</ol>
                  <p className={item.correct ? "correct-answer" : "selected-wrong-answer"}>
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
          </details>
        ) : (
          <p className="all-correct-message">모든 문제를 맞혔습니다.</p>
        )}

        <section className="recall-feedback quiz-feedback" aria-labelledby="quiz-feedback-title">
          <h2 id="quiz-feedback-title">훈련 피드백</h2>
          <p className="quiz-feedback-summary">저장된 훈련 기록 기준으로 정답률 {percentage}%를 기록했어요.</p>
          {reviewQuestions.length > 0 && <div className="quiz-feedback-cards">
            {reviewQuestions.map(q => <article className="quiz-feedback-card" key={q.id}>
              <h3>질문 {q.questionOrder} · 다시 확인하기</h3>
              <p>{q.content}</p>
              <dl>
                <div><dt>내 선택</dt><dd>{q.answer.content}</dd></div>
                <div><dt>저장된 정답</dt><dd>{q.answer.correctOption}</dd></div>
              </dl>
              <p className="muted">확인할 단서: {q.displayPhotoIds?.length
                ? "이 질문에 사용된 사진과 보기 내용을 다시 살펴보세요."
                : "질문에서 묻는 내용을 읽고, 선택한 보기와 저장된 정답을 비교해보세요."}</p>
              <button type="button" onClick={() => showQuestion(q.id)} aria-label={`질문 ${q.questionOrder} 보기`}>해당 질문 보기 ↑</button>
            </article>)}
          </div>}
          <p role="status">{feedbackLabels[session.result?.feedbackStatus] || "생성 대기"}</p>
          {session.result?.feedbackStatus !== "COMPLETED" && <p className="muted">피드백 생성 상태와 관계없이 저장된 답변과 채점 결과를 확인할 수 있습니다.</p>}
          {session.result?.summary && <details className="quiz-feedback-saved"><summary>저장된 피드백 원문 보기</summary><p className="recall-text">{session.result.summary}</p></details>}
          {["PENDING", "FAILED"].includes(session.result?.feedbackStatus) && <button disabled={submitting} onClick={generateFeedback}>피드백 생성·재시도</button>}
          {session.result?.feedbackStatus === "PROCESSING" && <button disabled={submitting} onClick={() => setRevision(v => v + 1)}>결과 새로고침</button>}
        </section>
        <ErrorBox error={submissionError} />

        <RecallEnrollment sessionId={sessionId} completed canEnroll={false} />
        <div className="quiz-complete-actions">
          <button
            className="button"
            type="button"
            disabled={submitting}
            onClick={retrain}
          >
            같은 사진으로 새 훈련
          </button>
          <a className="button secondary-button" href={`#/albums/${encodeURIComponent(albumId)}`}>
            앨범으로 돌아가기
          </a>
        </div>
        <a href="#/mypage">내 훈련 이력</a>
        <section className="recall-history">
          <h2>같은 사진의 훈련 기록</h2>
          <ErrorBox error={historyError} retry={() => setHistoryRevision(v => v + 1)} />
          {history ? <>{history.content.length ? history.content.map(item => <a className="recall-history-item" key={item.id} href={`#/training/${item.id}`}>
            <span>{date(item.startedAt)}</span><Badge value={item.status} />
            {item.result?.correctCount != null && <span>정답 {item.result.correctCount} / {item.result.totalCount}</span>}
          </a>) : <p>이 사진의 기록이 없습니다.</p>}<Pager data={history} onPage={setHistoryPage} /></> : !historyError && <p role="status">이력을 불러오는 중…</p>}
        </section>
      </section>
    );
  }

  if (session.status !== "IN_PROGRESS") return <section className="card"><Badge value={session.status} /><p>현재 상태에서는 퀴즈를 제출할 수 없습니다.</p><a href="#/mypage">훈련 이력 보기</a></section>;

  if (!recallReady) return <section className="card quiz-page">
    <a className="back-link" href="#/mypage">← 마이페이지</a><h1>훈련 시작 전 안내</h1>
    <p>원한다면 이번 훈련을 마친 7일 후, 사진 없이 기억을 떠올리는 평가에 참여할 수 있어요. 참여하지 않아도 퀴즈를 진행할 수 있어요.</p>
    <RecallEnrollment sessionId={sessionId} completed={false} canEnroll onContinue={() => setRecallReady(true)} />
  </section>;

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
    if (selectedOption === undefined || actionLock.current) return;
    if (currentIndex !== questions.length - 1) {
      setCurrentIndex((current) => current + 1);
      window.scrollTo(0, 0);
      return;
    }

    try {
      actionLock.current = true;
      setSubmitting(true);
      setSubmissionError("");
      const submittedAnswers = questions.map((item) => ({
        questionId: item.id,
        selectedOptionIndex: answers[item.id],
      }));
      const result = await api.submitQuiz(sessionId, submittedAnswers);
      const saved = result.session || await api.detail(sessionId);
      setSession(saved);
      setScoreResult(savedScore(saved));
      setHistoryRevision(v => v + 1);
      window.scrollTo(0, 0);
    } catch (err) {
      setSubmissionError(err.message || "퀴즈를 채점하지 못했습니다.");
      if (err.status === 409) {
        try {
          const saved = await api.detail(sessionId);
          if (savedScore(saved)) { setSession(saved); setScoreResult(savedScore(saved)); setSubmissionError(""); }
        } catch { /* Keep selections so a network failure can be retried. */ }
      }
    } finally {
      actionLock.current = false;
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
                  disabled={submitting}
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
