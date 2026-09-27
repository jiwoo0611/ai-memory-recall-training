import React, { useEffect, useState } from "react";
import { api } from "../api/client";
import PhotoImage from "../components/PhotoImage";

function formatBytes(value) {
  if (!Number.isFinite(value)) return "미등록";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTakenAt(value) {
  if (!value) return "미등록";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return date.toLocaleString("ko-KR");
}

function MetadataRow({ label, value }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || "미등록"}</dd>
    </div>
  );
}

export default function PhotoDetailPage({
  albumId,
  photoId,
}) {
  const [album, setAlbum] = useState(null);
  const [photo, setPhoto] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    async function loadDetail() {
      try {
        setLoading(true);
        setError("");

        const [albumData, photoData] = await Promise.all([
          api.album(albumId, controller.signal),
          api.photo(albumId, photoId, controller.signal),
        ]);

        setAlbum(albumData);
        setPhoto(photoData);
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(
            err.message || "사진 정보를 불러오지 못했습니다.",
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

  if (loading) {
    return (
      <section className="card">
        <p className="loading" role="status">
          사진 정보를 불러오는 중입니다…
        </p>
      </section>
    );
  }

  if (error || !photo) {
    return (
      <section className="card">
        <a className="back-link" href="#/mypage">
          ← 마이페이지
        </a>
        <p className="error" role="alert">
          {error || "사진 정보를 찾을 수 없습니다."}
        </p>
      </section>
    );
  }

  const displayTakenAt =
    photo.confirmedTakenOn ||
    formatTakenAt(photo.metadataTakenAt);
  const displayLocation =
    photo.confirmedLocation ||
    photo.metadataLocation ||
    "미등록";

  return (
    <section className="card photo-detail-page">
      <a className="back-link" href="#/mypage">
        ← 마이페이지
      </a>

      <div className="photo-detail-title">
        <p className="eyebrow">
          {album?.title || "사진 상세"}
        </p>
        <h1>{photo.originalFilename}</h1>
      </div>

      <div className="photo-detail-layout">
        <div>
          <PhotoImage
            albumId={albumId}
            photo={photo}
            variant="detail"
          />
        </div>

        <div className="photo-detail-info">
          <section>
            <h2>사진 정보</h2>
            <dl className="photo-metadata">
              <MetadataRow
                label="파일명"
                value={photo.originalFilename}
              />
              <MetadataRow
                label="파일 형식"
                value={photo.contentType}
              />
              <MetadataRow
                label="파일 크기"
                value={formatBytes(photo.sizeBytes)}
              />
              <MetadataRow
                label="앨범"
                value={album?.title}
              />
            </dl>
          </section>

          <section>
            <h2>촬영 정보</h2>
            <dl className="photo-metadata">
              <MetadataRow
                label="촬영일"
                value={displayTakenAt}
              />
              <MetadataRow
                label="사진에서 추출한 촬영일"
                value={formatTakenAt(photo.metadataTakenAt)}
              />
              <MetadataRow
                label="장소"
                value={displayLocation}
              />
              <MetadataRow
                label="사진에서 추출한 위치"
                value={photo.metadataLocation}
              />
            </dl>
          </section>
        </div>
      </div>
    </section>
  );
}
