import React, { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import { Empty } from "../components/UI";

const questionTypeLabels = {
  PHOTO_CLUE: "사진 단서",
  EXPERIENCE: "경험 회상",
  CONTEXT: "상황 회상",
  INFORMATION_CHECK: "정보 확인",
};

const sessionStatusLabels = {
  CREATED: "질문 생성 전",
  GENERATING_QUESTIONS: "질문 생성 중",
  IN_PROGRESS: "답변 진행 중",
  COMPLETED: "완료",
  QUESTION_GENERATION_FAILED: "질문 생성 실패",
};

const confidenceLabels = {
  HIGH: "높은 확률",
  MEDIUM: "중간 확률",
  LOW: "낮은 확률",
};

function formatTakenAt(value) {
  if (!value) return "";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return date.toLocaleString("ko-KR");
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function extractClues(value) {
  if (!value?.trim()) return [];

  let parsed;

  try {
    parsed = JSON.parse(value);
  } catch {
    return unique(
      value
        .split(/[\n,]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    );
  }

  const clues = [];
  const add = (item, prefix = "") => {
    if (typeof item !== "string" || !item.trim()) return;
    clues.push(`${prefix}${item.trim()}`);
  };
  const addAll = (items, prefix = "") => {
    if (Array.isArray(items)) {
      items.forEach((item) => add(item, prefix));
    }
  };

  add(parsed.summary);
  add(parsed.scene, "장면: ");

  if (Array.isArray(parsed.foods)) {
    parsed.foods.forEach((food) => {
      if (!food?.name) return;
      const confidence =
        confidenceLabels[food.confidence] || food.confidence;
      add(
        confidence
          ? `${food.name} · ${confidence}`
          : food.name,
        "음식: ",
      );
    });
  }

  addAll(parsed.objects, "사물: ");
  addAll(parsed.activities, "활동: ");
  addAll(parsed.visibleText, "사진 속 글자: ");
  addAll(parsed.questionClues);

  if (Number.isInteger(parsed.peopleCount) && parsed.peopleCount > 0) {
    clues.push(`사진에 보이는 사람 ${parsed.peopleCount}명`);
  }

  if (!clues.length) {
    const collectLegacy = (item) => {
      if (typeof item === "string") {
        add(item);
      } else if (Array.isArray(item)) {
        item.forEach(collectLegacy);
      } else if (item && typeof item === "object") {
        collectLegacy(
          item.clue ??
            item.text ??
            item.label ??
            item.description ??
            item.value,
        );
      }
    };

    collectLegacy(parsed);
  }

  return unique(clues);
}

function ContextCard({ title, values, emptyText }) {
  return (
    <section className="quiz-context-card">
      <h2>{title}</h2>
      {values.length ? (
        <ul className="quiz-clue-list">
          {values.map((value) => (
            <li key={value}>{value}</li>
          ))}
        </ul>
      ) : (
        <p className="muted">{emptyText}</p>
      )}
    </section>
  );
}

export default function PhotoDetailPage({ albumId, photoId }) {
  const [album, setAlbum] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [quizSessions, setQuizSessions] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState("");
  const [analysisNotice, setAnalysisNotice] = useState("");
  const [generatingQuestions, setGeneratingQuestions] = useState(false);
  const [questionGenerationError, setQuestionGenerationError] = useState("");
  const [questionGenerationNotice, setQuestionGenerationNotice] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    async function loadDetail() {
      try {
        setLoading(true);
        setError("");

        const [albumData, selectedPhoto, albumPhotoData, sessionPage] =
          await Promise.all([
            api.album(albumId, controller.signal),
            api.photo(albumId, photoId, controller.signal),
            api.albumPhotos(albumId, controller.signal),
            api.sessions(0, controller.signal, 100),
          ]);

        const normalizedPhotos = albumPhotoData.some(
          (photo) => photo.id === selectedPhoto.id,
        )
          ? albumPhotoData
          : [...albumPhotoData, selectedPhoto];
        const albumPhotoIds = new Set(
          normalizedPhotos.map((photo) => photo.id),
        );
        const matchingSessions = sessionPage.content.filter((session) =>
          albumPhotoIds.has(session.photo.id),
        );
        const sessionDetails = await Promise.all(
          matchingSessions.map((session) =>
            api.detail(session.id, controller.signal),
          ),
        );

        setAlbum(albumData);
        setPhotos(normalizedPhotos);
        setQuizSessions(sessionDetails);
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(
            err.message || "질문 생성 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadDetail();
    return () => controller.abort();
  }, [albumId, photoId]);

  const selectedPhoto = useMemo(
    () =>
      photos.find(
        (photo) => String(photo.id) === String(photoId),
      ),
    [photos, photoId],
  );

  const questionContext = useMemo(() => {
    const dates = unique(
      photos.map(
        (photo) =>
          photo.confirmedTakenOn ||
          formatTakenAt(photo.metadataTakenAt),
      ),
    );
    const locations = unique(
      photos.map(
        (photo) =>
          photo.confirmedLocation || photo.metadataLocation,
      ),
    );
    const aiClues = unique(
      photos.flatMap((photo) => extractClues(photo.aiClues)),
    );

    return { dates, locations, aiClues };
  }, [photos]);

  const questions = useMemo(
    () =>
      quizSessions.flatMap((session) =>
        session.questions.map((question) => ({
          ...question,
          sessionId: session.id,
          sessionStatus: session.status,
        })),
      ),
    [quizSessions],
  );

  async function handleAnalyze() {
    try {
      setAnalyzing(true);
      setAnalysisError("");
      setAnalysisNotice("");

      const analyzedPhoto = await api.analyzePhoto(albumId, photoId);
      setPhotos((current) =>
        current.map((photo) =>
          String(photo.id) === String(analyzedPhoto.id)
            ? analyzedPhoto
            : photo,
        ),
      );
      setAnalysisNotice(
        "분석 결과를 저장했습니다. 앨범의 AI 분석 단서에 반영되었습니다.",
      );
    } catch (err) {
      setAnalysisError(
        err.message || "사진 AI 분석에 실패했습니다.",
      );
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleGenerateQuestions() {
    try {
      setGeneratingQuestions(true);
      setQuestionGenerationError("");
      setQuestionGenerationNotice("");

      const generatedSession = await api.generateAlbumQuestions(albumId);
      setQuizSessions((current) => [
        generatedSession,
        ...current.filter(
          (session) => session.id !== generatedSession.id,
        ),
      ]);
      setQuestionGenerationNotice(
        "앨범 전체 사진의 단서를 반영한 질문 3개를 저장했습니다.",
      );
    } catch (err) {
      setQuestionGenerationError(
        err.message || "앨범 질문 생성에 실패했습니다.",
      );
    } finally {
      setGeneratingQuestions(false);
    }
  }
  if (loading) {
    return (
      <section className="card">
        <p className="loading" role="status">
          질문 생성 정보를 불러오는 중입니다…
        </p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card">
        <a className="back-link" href="#/mypage">
          ← 마이페이지
        </a>
        <p className="error" role="alert">
          {error}
        </p>
      </section>
    );
  }

  return (
    <section className="card photo-detail-page">
      <a className="back-link" href="#/mypage">
        ← 마이페이지
      </a>

      <div className="photo-detail-title">
        <p className="eyebrow">ALBUM QUIZ</p>
        <h1>{album?.title || "앨범"} 회상 질문</h1>
        <p className="muted">
          앨범의 사진 {photos.length}장에서 확인한 정보를 질문 생성
          자료로 사용합니다.
        </p>
      </div>

      <section className="photo-analysis-panel">
        <div>
          <p className="eyebrow">SELECTED PHOTO</p>
          <h2>{selectedPhoto?.originalFilename || "선택한 사진"}</h2>
          <p className="hint">
            분석을 실행하면 이 사진이 Google Gemini API로
            전송되고, 결과는 사진의 aiClues에 저장됩니다.
          </p>
        </div>
        <button
          className="button"
          type="button"
          onClick={handleAnalyze}
          disabled={analyzing}
        >
          {analyzing
            ? "사진 분석 중…"
            : selectedPhoto?.aiClues
              ? "AI 다시 분석"
              : "AI 사진 분석"}
        </button>
      </section>

      {analysisError ? (
        <p className="error" role="alert">
          {analysisError}
        </p>
      ) : null}
      {analysisNotice ? (
        <p className="notice" role="status">
          {analysisNotice}
        </p>
      ) : null}

      <section className="quiz-overview">
        <div className="section-heading">
          <div>
            <p className="eyebrow">QUESTION CONTEXT</p>
            <h2>질문 생성에 사용할 자료</h2>
          </div>
        </div>

        <div className="quiz-context-grid">
          <ContextCard
            title="촬영 시점"
            values={questionContext.dates}
            emptyText="확인된 촬영 시점이 없습니다."
          />
          <ContextCard
            title="장소 단서"
            values={questionContext.locations}
            emptyText="확인된 장소 정보가 없습니다."
          />
          <ContextCard
            title="AI 분석 단서"
            values={questionContext.aiClues}
            emptyText="아직 AI 분석 단서가 저장되지 않았습니다."
          />
        </div>
      </section>

      <section className="quiz-questions">
        <div className="section-heading">
          <div>
            <p className="eyebrow">GENERATED QUESTIONS</p>
            <h2>
              생성된 회상 질문{" "}
              <span className="count">{questions.length}</span>
            </h2>
          </div>
          <button
            className="button"
            type="button"
            onClick={handleGenerateQuestions}
            disabled={generatingQuestions || !photos.length}
          >
            {generatingQuestions
              ? "질문 생성 중…"
              : "앨범 질문 3개 생성"}
          </button>
        </div>

        {questionGenerationError ? (
          <p className="error" role="alert">
            {questionGenerationError}
          </p>
        ) : null}
        {questionGenerationNotice ? (
          <p className="notice" role="status">
            {questionGenerationNotice}
          </p>
        ) : null}

        {!quizSessions.length ? (
          <Empty>
            아직 이 앨범에서 생성된 질문이 없습니다. 위 버튼을 눌러
            앨범 전체 사진의 단서로 질문을 만들어보세요.
          </Empty>
        ) : !questions.length ? (
          <div className="quiz-session-status">
            {quizSessions.map((session) => (
              <p key={session.id}>
                훈련 #{session.id} ·{" "}
                {sessionStatusLabels[session.status] || session.status}
              </p>
            ))}
          </div>
        ) : (
          <ol className="quiz-question-list">
            {questions.map((question) => (
              <li key={`${question.sessionId}-${question.id}`}>
                <div className="quiz-question-meta">
                  <span>
                    {questionTypeLabels[question.type] || question.type}
                  </span>
                  <span>
                    {sessionStatusLabels[question.sessionStatus] ||
                      question.sessionStatus}
                  </span>
                </div>
                <p>{question.content}</p>
              </li>
            ))}
          </ol>
        )}
      </section>
    </section>
  );
}