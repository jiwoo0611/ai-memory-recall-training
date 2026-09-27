import React, { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";

const confidenceLabels = {
  HIGH: "높은 확률",
  MEDIUM: "중간 확률",
  LOW: "낮은 확률",
};

function formatTakenAt(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
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
    if (typeof item === "string" && item.trim()) {
      clues.push(`${prefix}${item.trim()}`);
    }
  };
  const addAll = (items, prefix = "") => {
    if (Array.isArray(items)) {
      items.forEach((item) => add(item, prefix));
    }
  };

  if (Array.isArray(parsed.foods)) {
    parsed.foods.forEach((food) => {
      if (!food?.name) return;
      const confidence = confidenceLabels[food.confidence] || food.confidence;
      add(
        confidence ? `${food.name} · ${confidence}` : food.name,
        "음식: ",
      );
    });
  }
  addAll(parsed.objects, "사물: ");
  addAll(parsed.activities, "활동: ");
  addAll(parsed.visibleText, "사진 속 글자: ");
  addAll(parsed.questionClues);

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
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState("");
  const [analysisNotice, setAnalysisNotice] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    async function loadDetail() {
      try {
        setLoading(true);
        setError("");

        const [albumData, selectedPhoto, albumPhotoData] = await Promise.all([
          api.album(albumId, controller.signal),
          api.photo(albumId, photoId, controller.signal),
          api.albumPhotos(albumId, controller.signal),
        ]);

        const normalizedPhotos = albumPhotoData.some(
          (photo) => photo.id === selectedPhoto.id,
        )
          ? albumPhotoData
          : [...albumPhotoData, selectedPhoto];

        setAlbum(albumData);
        setPhotos(normalizedPhotos);
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(err.message || "사진 정보를 불러오지 못했습니다.");
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
      photos.map((photo) => formatTakenAt(photo.metadataTakenAt)),
    );
    const locations = unique(
      photos.map((photo) => photo.metadataLocation),
    );
    const aiClues = unique(
      photos.flatMap((photo) => extractClues(photo.aiClues)),
    );
    return { dates, locations, aiClues };
  }, [photos]);

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
        "분석 결과를 저장했습니다. 앨범 질문 생성에 이 단서가 사용됩니다.",
      );
    } catch (err) {
      setAnalysisError(err.message || "사진 AI 분석에 실패했습니다.");
    } finally {
      setAnalyzing(false);
    }
  }

  if (loading) {
    return (
      <section className="card">
        <p className="loading" role="status">
          사진 정보를 불러오는 중입니다…
        </p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card">
        <a className="back-link" href={`#/albums/${encodeURIComponent(albumId)}`}>
          ← 앨범 사진 목록
        </a>
        <p className="error" role="alert">{error}</p>
      </section>
    );
  }

  return (
    <section className="card photo-detail-page">
      <a className="back-link" href={`#/albums/${encodeURIComponent(albumId)}`}>
        ← 앨범 사진 목록
      </a>

      <div className="photo-detail-title">
        <p className="eyebrow">PHOTO CLUES</p>
        <h1>{selectedPhoto?.originalFilename || "사진"} 분석 정보</h1>
        <p className="muted">
          질문 생성은 {album?.title || "앨범"} 앨범 화면에서 실행할 수 있습니다.
        </p>
      </div>

      <section className="photo-analysis-panel">
        <div>
          <p className="eyebrow">SELECTED PHOTO</p>
          <h2>AI 분석 단서</h2>
          <p className="hint">
            분석을 실행하면 이 사진이 Google Gemini API로 전송되고,
            결과는 사진의 aiClues에 저장됩니다.
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

      {analysisError ? <p className="error" role="alert">{analysisError}</p> : null}
      {analysisNotice ? <p className="notice" role="status">{analysisNotice}</p> : null}

      <section className="quiz-overview">
        <div className="section-heading">
          <div>
            <p className="eyebrow">QUESTION EVIDENCE</p>
            <h2>앨범 질문에 사용할 사진 정보</h2>
          </div>
        </div>

        <div className="quiz-context-grid">
          <ContextCard
            title="촬영 시점"
            values={questionContext.dates}
            emptyText="EXIF 촬영 시점이 없습니다."
          />
          <ContextCard
            title="장소 단서"
            values={questionContext.locations}
            emptyText="EXIF 장소 정보가 없습니다."
          />
          <ContextCard
            title="AI 분석 단서"
            values={questionContext.aiClues}
            emptyText="아직 AI 분석 단서가 저장되지 않았습니다."
          />
        </div>
      </section>
    </section>
  );
}
