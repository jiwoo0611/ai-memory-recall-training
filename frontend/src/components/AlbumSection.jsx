import React, { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { Empty } from "./UI";

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("ko-KR");
}

function PhotoThumbnail({ albumId, photo }) {
  const [imageUrl, setImageUrl] = useState("");
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let objectUrl = "";

    async function loadImage() {
      try {
        setImageError(false);

        const blob = await api.photoImage(
          albumId,
          photo.id,
          controller.signal,
        );

        objectUrl = URL.createObjectURL(blob);
        setImageUrl(objectUrl);
      } catch (error) {
        if (error.name !== "AbortError") {
          setImageError(true);
        }
      }
    }

    loadImage();

    return () => {
      controller.abort();

      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [albumId, photo.id]);

  if (imageError) {
    return (
      <div className="file-mark" aria-hidden="true">
        ▧
      </div>
    );
  }

  if (!imageUrl) {
    return (
      <div className="file-mark" aria-hidden="true">
        불러오는 중...
      </div>
    );
  }

  return (
    <img
      src={imageUrl}
      alt={photo.originalFilename}
      style={{
        width: "100%",
        height: "180px",
        objectFit: "cover",
        borderRadius: "12px",
        display: "block",
        marginBottom: "12px",
      }}
    />
  );
}

export default function AlbumSection() {
  const [albums, setAlbums] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [selectedAlbum, setSelectedAlbum] = useState(null);
  const [albumPhotos, setAlbumPhotos] = useState([]);

  const [albumTitle, setAlbumTitle] = useState("");
  const [creating, setCreating] = useState(false);

  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  const [deletingPhotoId, setDeletingPhotoId] = useState(null);
  const [deletingAlbumId, setDeletingAlbumId] = useState(null);

  const fileInputRef = useRef(null);

  const loadAlbums = async () => {
    try {
      setLoading(true);
      setError("");

      const data = await api.albums();
      setAlbums(data);
    } catch (err) {
      setError(
        err.message || "앨범을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAlbums();
  }, []);

  const handleCreateAlbum = async (event) => {
    event.preventDefault();

    const title = albumTitle.trim();

    if (!title) {
      setError("앨범 이름을 입력해주세요.");
      return;
    }

    if (title.length > 10) {
      setError("앨범 이름은 10자 이하여야 합니다.");
      return;
    }

    try {
      setCreating(true);
      setError("");

      const newAlbum = await api.createAlbum(title);

      setAlbums((previous) => [
        ...previous,
        newAlbum,
      ]);

      setAlbumTitle("");
    } catch (err) {
      setError(
        err.message || "앨범 생성에 실패했습니다.",
      );
    } finally {
      setCreating(false);
    }
  };

  const handleOpenAlbum = async (album) => {
    try {
      setSelectedAlbum(album);
      setAlbumPhotos([]);
      setUploadError("");
      setError("");

      const photos = await api.albumPhotos(album.id);
      setAlbumPhotos(photos);
    } catch (err) {
      setError(
        err.message ||
          "앨범의 사진을 불러오지 못했습니다.",
      );
    }
  };

  const handleUpload = async (event) => {
    const files = Array.from(event.target.files || []);

    if (!files.length || !selectedAlbum) {
      return;
    }

    try {
      setUploading(true);
      setUploadError("");

      await api.uploadPhotos(
        selectedAlbum.id,
        files,
      );

      const photos = await api.albumPhotos(
        selectedAlbum.id,
      );

      setAlbumPhotos(photos);
    } catch (err) {
      setUploadError(
        err.message || "사진 업로드에 실패했습니다.",
      );
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  };

  const handleDeletePhoto = async (photo) => {
    if (!selectedAlbum) return;

    const confirmed = window.confirm(
      `"${photo.originalFilename}" 사진을 삭제할까요?`,
    );

    if (!confirmed) return;

    try {
      setDeletingPhotoId(photo.id);
      setUploadError("");

      await api.deletePhoto(
        selectedAlbum.id,
        photo.id,
      );

      setAlbumPhotos((previous) =>
        previous.filter(
          (item) => item.id !== photo.id,
        ),
      );
    } catch (err) {
      setUploadError(
        err.message || "사진 삭제에 실패했습니다.",
      );
    } finally {
      setDeletingPhotoId(null);
    }
  };

  const handleDeleteAlbum = async (event, album) => {
    event.stopPropagation();

    const confirmed = window.confirm(
      `"${album.title}" 앨범을 삭제할까요?\n앨범 안의 사진도 모두 삭제됩니다.`,
    );

    if (!confirmed) return;

    try {
      setDeletingAlbumId(album.id);
      setError("");

      await api.deleteAlbum(album.id);

      setAlbums((previous) =>
        previous.filter(
          (item) => item.id !== album.id,
        ),
      );
    } catch (err) {
      setError(
        err.message || "앨범 삭제에 실패했습니다.",
      );
    } finally {
      setDeletingAlbumId(null);
    }
  };

  if (selectedAlbum) {
    return (
      <section className="card">
        <div className="section-heading">
          <div>
            <button
              type="button"
              onClick={() => {
                setSelectedAlbum(null);
                setAlbumPhotos([]);
                setUploadError("");
              }}
            >
              ← 앨범 목록
            </button>

            <h2>{selectedAlbum.title}</h2>
          </div>

          <div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              multiple
              style={{ display: "none" }}
              onChange={handleUpload}
            />

            <button
              type="button"
              disabled={uploading}
              onClick={() =>
                fileInputRef.current?.click()
              }
            >
              {uploading
                ? "업로드 중..."
                : "+ 사진 추가"}
            </button>
          </div>
        </div>

        {uploadError && (
          <p className="error" role="alert">
            {uploadError}
          </p>
        )}

        {!albumPhotos.length ? (
          <Empty>
            아직 이 앨범에 등록된 사진이 없습니다.
          </Empty>
        ) : (
          <div className="photo-grid">
            {albumPhotos.map((photo) => (
              <article
                className="photo-item"
                key={photo.id}
              >
                <PhotoThumbnail
                  albumId={selectedAlbum.id}
                  photo={photo}
                />

                <h3>{photo.originalFilename}</h3>

                <p className="muted">
                  {photo.contentType} ·{" "}
                  {(photo.sizeBytes / 1024).toFixed(1)} KB
                </p>

                <dl>
                  <div>
                    <dt>촬영일</dt>
                    <dd>
                      {photo.confirmedTakenOn ||
                        photo.metadataTakenAt ||
                        "미등록"}
                    </dd>
                  </div>

                  <div>
                    <dt>장소</dt>
                    <dd>
                      {photo.confirmedLocation ||
                        photo.metadataLocation ||
                        "미등록"}
                    </dd>
                  </div>
                </dl>

                <button
                  type="button"
                  disabled={deletingPhotoId === photo.id}
                  onClick={() =>
                    handleDeletePhoto(photo)
                  }
                  style={{
                    marginTop: "16px",
                  }}
                >
                  {deletingPhotoId === photo.id
                    ? "삭제 중..."
                    : "삭제"}
                </button>
              </article>
            ))}
          </div>
        )}
      </section>
    );
  }

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <p className="eyebrow">
            MY ALBUMS
          </p>

          <h2>
            나의 앨범{" "}
            <span className="count">
              {albums.length}
            </span>
          </h2>
        </div>
      </div>

      <form
        onSubmit={handleCreateAlbum}
        style={{
          display: "flex",
          gap: "8px",
          marginBottom: "24px",
        }}
      >
        <input
          type="text"
          value={albumTitle}
          maxLength={10}
          placeholder="새 앨범 이름"
          onChange={(event) =>
            setAlbumTitle(event.target.value)
          }
        />

        <button
          type="submit"
          disabled={creating}
        >
          {creating
            ? "생성 중..."
            : "+ 새 앨범"}
        </button>
      </form>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {loading ? (
        <p className="loading">
          앨범을 불러오는 중입니다…
        </p>
      ) : !albums.length ? (
        <Empty>
          아직 생성된 앨범이 없습니다.
        </Empty>
      ) : (
        <div className="photo-grid">
          {albums.map((album) => (
            <article
              className="photo-item"
              key={album.id}
              onClick={() =>
                handleOpenAlbum(album)
              }
              style={{
                textAlign: "left",
                cursor: "pointer",
              }}
            >
              <div
                className="file-mark"
                aria-hidden="true"
              >
                📁
              </div>

              <h3>{album.title}</h3>

              <p className="muted">
                생성일 {formatDate(album.createdAt)}
              </p>

              <button
                type="button"
                disabled={deletingAlbumId === album.id}
                onClick={(event) =>
                  handleDeleteAlbum(event, album)
                }
                style={{
                  marginTop: "16px",
                }}
              >
                {deletingAlbumId === album.id
                  ? "삭제 중..."
                  : "앨범 삭제"}
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
