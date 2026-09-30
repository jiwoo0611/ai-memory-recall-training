import React, { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { Empty } from "./UI";
import PhotoImage from "./PhotoImage";

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("ko-KR");
}

export default function AlbumSection({ albumId }) {
  const [albums, setAlbums] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [selectedAlbum, setSelectedAlbum] = useState(null);
  const [albumPhotos, setAlbumPhotos] = useState([]);
  const [quizSessions, setQuizSessions] = useState([]);
  const [generatingQuestions, setGeneratingQuestions] = useState(false);
  const [resettingQuestions, setResettingQuestions] = useState(false);
  const [questionGenerationError, setQuestionGenerationError] = useState("");
  const [questionGenerationNotice, setQuestionGenerationNotice] = useState("");

  const [albumTitle, setAlbumTitle] = useState("");
  const [creating, setCreating] = useState(false);

  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  const [deletingPhotoId, setDeletingPhotoId] = useState(null);
  const [deletingAlbumId, setDeletingAlbumId] = useState(null);

  // 앨범 이름 변경
  const [editingAlbumId, setEditingAlbumId] = useState(null);
  const [editingAlbumTitle, setEditingAlbumTitle] = useState(null);
  const [updatingAlbumId, setUpdatingAlbumId] = useState(null);

  // 사진 선택 삭제
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedPhotoIds, setSelectedPhotoIds] = useState([]);
  const [deletingSelected, setDeletingSelected] = useState(false);

  const fileInputRef = useRef(null);

  useEffect(() => {
    const controller = new AbortController();

    async function load() {
      setLoading(true);
      setError("");

      try {
        if (albumId) {
          const [album, photos, sessionPage] = await Promise.all([
            api.album(albumId, controller.signal),
            api.albumPhotos(albumId, controller.signal),
            api.sessions(0, controller.signal, 100),
          ]);

          const photoIds = new Set(photos.map((photo) => photo.id));

          const matchingSessions = sessionPage.content.filter((session) =>
            photoIds.has(session.photo.id),
          );

          const sessionDetails = await Promise.all(
            matchingSessions.map((session) =>
              api.detail(session.id, controller.signal),
            ),
          );

          if (!controller.signal.aborted) {
            setSelectedAlbum(album);
            setAlbumPhotos(photos);
            setQuizSessions(sessionDetails);
          }
        } else {
          const albums = await api.albums(controller.signal);

          if (!controller.signal.aborted) {
            setAlbums(albums);
          }
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || "앨범을 불러오지 못했습니다.");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    load();

    return () => controller.abort();
  }, [albumId]);

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

  /*
   * 사진 업로드
   *
   * 앨범 목록에서 업로드:
   *   사진 위치 분석 → 자동 앨범 생성 → 앨범 목록 갱신
   *
   * 앨범 상세에서 업로드:
   *   업로드 완료 후 현재 앨범 사진 목록 갱신
   */
  const handleUpload = async (event) => {
    const files = Array.from(event.target.files || []);

    if (!files.length) {
      return;
    }

    try {
      setUploading(true);
      setUploadError("");

      setUploadError(`사진 ${files.length}장 저장 중… 파일 크기에 따라 최대 30초 정도 걸릴 수 있습니다.`);
      await api.uploadPhotos(files);
      setUploadError("");

      if (selectedAlbum) {
        const photos = await api.albumPhotos(
          selectedAlbum.id,
        );

        setAlbumPhotos(photos);
      } else {
        const updatedAlbums = await api.albums();

        setAlbums(updatedAlbums);
      }
    } catch (err) {
      setUploadError(
        err.message || "사진 업로드에 실패했습니다.",
      );
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  };

const handleStartEditAlbum = (event, album) => {
  event.preventDefault();
  event.stopPropagation();

  setEditingAlbumId(album.id);
  setEditingAlbumTitle(album.title);
  setError("");
};

const handleCancelEditAlbum = (event) => {
  event.preventDefault();
  event.stopPropagation();

  setEditingAlbumId(null);
  setEditingAlbumTitle("");
};

const handleUpdateAlbumTitle = async (event, album) => {
  event.preventDefault();
  event.stopPropagation();

  const title = editingAlbumTitle.trim();

  if (!title) {
    setError("앨범 이름을 입력해주세요.");
    return;
  }

  if (title.length > 10) {
    setError("앨범 이름은 10자 이하여야 합니다.");
    return;
  }

  try {
    setUpdatingAlbumId(album.id);
    setError("");

    const updatedAlbum = await api.updateAlbumTitle(
      album.id,
      title,
    );

    setAlbums((previous) =>
      previous.map((item) =>
        item.id === album.id ? updatedAlbum : item,
      ),
    );

    setEditingAlbumId(null);
    setEditingAlbumTitle("");
  } catch (err) {
    setError(
      err.message || "앨범 이름 변경에 실패했습니다.",
    );
  } finally {
    setUpdatingAlbumId(null);
  }
};

  const handleDeletePhoto = async (photo) => {
    if (!selectedAlbum) return;

    const confirmed = window.confirm(
      `"${photo.originalFilename}" 사진과 연결된 모든 훈련·질문·답변·결과를 삭제할까요? 삭제 후 복구할 수 없습니다.`,
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

  const handleTogglePhoto = (photoId) => {
    setSelectedPhotoIds((previous) => {
      if (previous.includes(photoId)) {
        return previous.filter(
          (id) => id !== photoId,
        );
      }

      return [...previous, photoId];
    });
  };

  const handleSelectAll = () => {
    if (
      selectedPhotoIds.length === albumPhotos.length
    ) {
      setSelectedPhotoIds([]);
      return;
    }

    setSelectedPhotoIds(
      albumPhotos.map((photo) => photo.id),
    );
  };

  const handleCancelSelection = () => {
    setSelectionMode(false);
    setSelectedPhotoIds([]);
  };

  const handleDeleteSelected = async () => {
    if (!selectedAlbum || !selectedPhotoIds.length) {
      return;
    }

    const confirmed = window.confirm(
      `선택한 사진 ${selectedPhotoIds.length}장과 연결된 모든 훈련·질문·답변·결과를 삭제할까요? 삭제 후 복구할 수 없습니다.`,
    );

    if (!confirmed) return;

    try {
      setDeletingSelected(true);
      setUploadError("");

      await api.deleteSelectedPhotos(
        selectedAlbum.id,
        selectedPhotoIds,
      );

      setAlbumPhotos((previous) =>
        previous.filter(
          (photo) =>
            !selectedPhotoIds.includes(photo.id),
        ),
      );

      setSelectedPhotoIds([]);
      setSelectionMode(false);
    } catch (err) {
      setUploadError(
        err.message ||
          "선택한 사진 삭제에 실패했습니다.",
      );
    } finally {
      setDeletingSelected(false);
    }
  };

  const handleGenerateQuestions = async () => {
    if (!selectedAlbum || !albumPhotos.length) {
      return;
    }

    try {
      setGeneratingQuestions(true);
      setQuestionGenerationError("");
      setQuestionGenerationNotice("");

      let updatedPhotos = albumPhotos;

      const photosToAnalyze = updatedPhotos.filter(
        (photo) => !photo.aiClues?.trim(),
      );

      const alreadyAnalyzedCount =
        updatedPhotos.length - photosToAnalyze.length;

      for (
        let index = 0;
        index < photosToAnalyze.length;
        index += 1
      ) {
        const photo = photosToAnalyze[index];

        setQuestionGenerationNotice(
          `전체 ${updatedPhotos.length}장 중 ${
            alreadyAnalyzedCount + index + 1
          }번째 사진 AI 분석 중…`,
        );

        const analyzedPhoto = await api.analyzePhoto(
          selectedAlbum.id,
          photo.id,
        );

        updatedPhotos = updatedPhotos.map((item) =>
          item.id === analyzedPhoto.id
            ? analyzedPhoto
            : item,
        );

        setAlbumPhotos(updatedPhotos);
      }

      setQuestionGenerationNotice(
        `사진 AI 분석 완료 (${updatedPhotos.length}/${updatedPhotos.length}) · 객관식 질문 생성 중…`,
      );

      const generatedSession =
        await api.generateAlbumQuestions(
          selectedAlbum.id,
        );

      setQuizSessions((current) => [
        generatedSession,
        ...current.filter(
          (session) =>
            session.id !== generatedSession.id,
        ),
      ]);

      setQuestionGenerationNotice(
        `앨범 전체 사진으로 회상 질문 ${generatedSession.questions.length}개를 만들었습니다.`,
      );

      location.hash =
        `#/albums/${encodeURIComponent(
          selectedAlbum.id,
        )}/quiz/${encodeURIComponent(
          generatedSession.id,
        )}`;
    } catch (err) {
      setQuestionGenerationNotice("");

      setQuestionGenerationError(
        err.message ||
          "앨범 질문 생성에 실패했습니다.",
      );
    } finally {
      setGeneratingQuestions(false);
    }
  };

  const handleResetQuestions = async () => {
    if (!selectedAlbum || !quizSessions.length) {
      return;
    }

    const confirmed = window.confirm(
      `"${selectedAlbum.title}" 앨범의 모든 훈련·질문·답변·결과를 영구 삭제할까요?\n사진과 AI 분석 정보는 유지됩니다. 재훈련은 결과 화면의 ‘같은 사진으로 새 훈련’을 이용해주세요.`,
    );

    if (!confirmed) return;

    try {
      setResettingQuestions(true);
      setQuestionGenerationError("");
      setQuestionGenerationNotice("");

      const result = await api.resetAlbumQuestions(
        selectedAlbum.id,
      );

      setQuizSessions([]);
      setQuestionGenerationNotice(
        `이 앨범의 기존 퀴즈 ${result.deletedSessionCount}개와 질문 ${result.deletedQuestionCount}개를 초기화했습니다.`,
      );
    } catch (err) {
      setQuestionGenerationError(
        err.message || "퀴즈 초기화에 실패했습니다.",
      );
    } finally {
      setResettingQuestions(false);
    }
  };

  const handleDeleteAlbum = async (event, album) => {
    event.stopPropagation();

    const confirmed = window.confirm(
      `"${album.title}" 앨범을 삭제할까요?\n앨범 안의 사진과 연결된 모든 훈련·질문·답변·결과가 함께 삭제되며 복구할 수 없습니다.`,
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

  /*
   * ==========================================
   * 앨범 상세 화면 로딩 / 오류
   * ==========================================
   */
  if (albumId && (loading || error)) {
    return (
      <section className="card">
        <a
          className="back-link"
          href="#/mypage"
        >
          ← 앨범 목록
        </a>

        {loading ? (
          <p
            className="loading"
            role="status"
          >
            앨범을 불러오는 중입니다…
          </p>
        ) : (
          <p
            className="error"
            role="alert"
          >
            {error}
          </p>
        )}
      </section>
    );
  }

  /*
   * ==========================================
   * 앨범 상세 화면
   * ==========================================
   */
  if (albumId && selectedAlbum) {
    return (
      <section className="card">
        <div className="section-heading">
          <div>
            <a
              className="back-link"
              href="#/mypage"
            >
              ← 앨범 목록
            </a>

            <h2>{selectedAlbum.title}</h2>
          </div>

          <div
            style={{
              display: "flex",
              gap: "8px",
              flexWrap: "wrap",
            }}
          >
            {albumPhotos.length > 0 && (
              <button
                className="button"
                type="button"
                onClick={handleGenerateQuestions}
                disabled={
                  generatingQuestions ||
                  deletingSelected
                }
              >
                {generatingQuestions
                  ? questionGenerationNotice ||
                    "퀴즈 준비 중…"
                  : "새 퀴즈 생성·시작"}
              </button>
            )}

            {!selectionMode ? (
              <>
                {albumPhotos.length > 0 && (
                  <button
                    type="button"
                    onClick={() =>
                      setSelectionMode(true)
                    }
                  >
                    선택
                  </button>
                )}

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
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={handleSelectAll}
                  disabled={deletingSelected}
                >
                  {selectedPhotoIds.length ===
                  albumPhotos.length
                    ? "전체 선택 해제"
                    : "전체 선택"}
                </button>

                <button
                  type="button"
                  onClick={handleDeleteSelected}
                  disabled={
                    deletingSelected ||
                    selectedPhotoIds.length === 0
                  }
                >
                  {deletingSelected
                    ? "삭제 중..."
                    : `선택 삭제 (${selectedPhotoIds.length})`}
                </button>

                <button
                  type="button"
                  onClick={handleCancelSelection}
                  disabled={deletingSelected}
                >
                  취소
                </button>
              </>
            )}
          </div>
        </div>

        {uploadError && (
          <p
            className="error"
            role="alert"
          >
            {uploadError}
          </p>
        )}

        {!albumPhotos.length ? (
          <Empty>
            아직 이 앨범에 등록된 사진이 없습니다.
          </Empty>
        ) : (
          <div className="photo-grid">
            {albumPhotos.map((photo, index) => {
              const isSelected =
                selectedPhotoIds.includes(photo.id);

              return (
                <article
                  className="photo-item"
                  key={photo.id}
                  style={{
                    position: "relative",
                    outline: isSelected
                      ? "3px solid currentColor"
                      : "none",
                    cursor: selectionMode
                      ? "pointer"
                      : "default",
                  }}
                  onClick={
                    selectionMode
                      ? () =>
                          handleTogglePhoto(
                            photo.id,
                          )
                      : undefined
                  }
                >
                  {selectionMode && (
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() =>
                        handleTogglePhoto(
                          photo.id,
                        )
                      }
                      onClick={(event) =>
                        event.stopPropagation()
                      }
                      aria-label={`${photo.originalFilename} 선택`}
                      style={{
                        position: "absolute",
                        top: "12px",
                        right: "12px",
                        width: "20px",
                        height: "20px",
                        zIndex: 2,
                      }}
                    />
                  )}

                  {selectionMode ? (
                    <div>
                      <PhotoImage
                        albumId={
                          selectedAlbum.id
                        }
                        photo={photo}
                      />

                      <h3>사진{index+1}</h3>

                      <p className="muted">
                        {photo.contentType} ·{" "}
                        {(
                          photo.sizeBytes /
                          1024
                        ).toFixed(1)}{" "}
                        KB
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
                    </div>
                  ) : (
                    <>
                      <a
                        className="photo-open"
                        href={`#/albums/${selectedAlbum.id}/photos/${photo.id}`}
                        aria-label={`${photo.originalFilename} 상세 보기`}
                      >
                        <PhotoImage
                          albumId={
                            selectedAlbum.id
                          }
                          photo={photo}
                        />

                        <h3>사진{index+1}</h3>

                        <p className="muted">
                          {photo.contentType} ·{" "}
                          {(
                            photo.sizeBytes /
                            1024
                          ).toFixed(1)}{" "}
                          KB
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
                      </a>

                      <button
                        type="button"
                        disabled={
                          deletingPhotoId ===
                          photo.id
                        }
                        onClick={() =>
                          handleDeletePhoto(
                            photo,
                          )
                        }
                        style={{
                          marginTop: "16px",
                        }}
                      >
                        {deletingPhotoId ===
                        photo.id
                          ? "삭제 중..."
                          : "삭제"}
                      </button>
                    </>
                  )}
                </article>
              );
            })}
          </div>
        )}

        <section className="album-question-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">
                ALBUM RECALL QUIZ
              </p>

              <h2>회상 퀴즈</h2>

              <p className="muted">
                앨범 전체의 AI 분석 단서와
                촬영 정보를 이용합니다. 문제에
                따라 관련 사진을 보여주거나 사진
                없이 기억을 떠올립니다.
              </p>
            </div>

            {quizSessions.length > 0 && (
              <button
                type="button"
                onClick={handleResetQuestions}
                disabled={
                  resettingQuestions ||
                  generatingQuestions
                }
              >
                {resettingQuestions
                  ? "초기화 중..."
                  : "이 앨범 퀴즈 초기화"}
              </button>
            )}
          </div>

          {questionGenerationError ? (
            <p
              className="error"
              role="alert"
            >
              {questionGenerationError}
            </p>
          ) : null}

          {questionGenerationNotice ? (
            <p
              className="notice"
              role="status"
            >
              {questionGenerationNotice}
            </p>
          ) : null}

          {!quizSessions.length ? (
            <Empty>
              아직 생성된 퀴즈가 없습니다.
              위의 ‘새 퀴즈 생성·시작’을
              눌러주세요.
            </Empty>
          ) : (
            <div className="quiz-session-list">
              {quizSessions.map((session) => {
                const questions =
                  session.questions || [];

                const compatible = questions.length > 0;

                const photoQuestionCount =
                  questions.filter(
                    (question) =>
                      question.displayPhotoIds
                        ?.length,
                  ).length;

                return (
                  <article
                    className="quiz-session-card"
                    key={session.id}
                  >
                    <div>
                      <p className="eyebrow">
                        회상 퀴즈 #
                        {session.id}
                      </p>

                      <h3>
                        회상 질문{" "}
                        {questions.length}개
                      </h3>

                      <p className="muted">
                        사진 제시{" "}
                        {photoQuestionCount}개 ·
                        사진 없이 회상{" "}
                        {questions.length -
                          photoQuestionCount}
                        개
                      </p>
                    </div>

                    {compatible ? (
                      <a
                        className="button"
                        href={`#/albums/${encodeURIComponent(
                          selectedAlbum.id,
                        )}/quiz/${encodeURIComponent(
                          session.id,
                        )}`}
                      >
                        {session.status === "COMPLETED" ? "저장된 결과 보기" : "답변 작성하기"}
                      </a>
                    ) : (
                      <span className="legacy-quiz-label">
                        아직 질문이 준비되지 않았습니다
                      </span>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </section>
    );
  }

  /*
   * ==========================================
   * 앨범 목록 화면
   * ==========================================
   */
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

      {/*
        자동 분류용 사진 업로드
        여러 장 선택 가능
      */}
      <div
        style={{
          display: "flex",
          gap: "8px",
          marginBottom: "24px",
          flexWrap: "wrap",
        }}
      >
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

      {uploadError && (
        <p
          className="error"
          role="alert"
        >
          {uploadError}
        </p>
      )}

      {/*
        기존 수동 앨범 생성 기능
        자동 분류 테스트가 끝날 때까지 유지
      */}
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
            setAlbumTitle(
              event.target.value,
            )
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
        <p
          className="error"
          role="alert"
        >
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
              style={{
                textAlign: "left",
              }}
            >
              <a
                className="photo-open"
                href={`#/albums/${encodeURIComponent(
                  album.id,
                )}`}
                aria-label={`${album.title} 앨범 열기`}
              >
                <div
                  className="file-mark"
                  aria-hidden="true"
                >
                  📁
                </div>

                <h3>
                  {album.photoCount ?? 0}장
                </h3>

                <p className="muted">
                  생성일{" "}
                  {formatDate(
                    album.createdAt,
                  )}
                </p>
              </a>

        {editingAlbumId === album.id ? (
          <form
            onSubmit={(event) =>
              handleUpdateAlbumTitle(event, album)
            }
            onClick={(event) => event.stopPropagation()}
            style={{
              marginTop: "16px",
              display: "flex",
              gap: "8px",
              flexWrap: "wrap",
            }}
          >
            <input
              type="text"
              value={editingAlbumTitle}
              maxLength={10}
              autoFocus
              placeholder="앨범 이름"
              onChange={(event) =>
                setEditingAlbumTitle(event.target.value)
              }
            />

            <button
              type="submit"
              disabled={updatingAlbumId === album.id}
            >
              {updatingAlbumId === album.id
                ? "저장 중..."
                : "저장"}
            </button>

            <button
              type="button"
              onClick={handleCancelEditAlbum}
              disabled={updatingAlbumId === album.id}
            >
              취소
            </button>
          </form>
        ) : (
          <button
            type="button"
            onClick={(event) =>
              handleStartEditAlbum(event, album)
            }
            style={{
              marginTop: "16px",
              marginRight: "8px",
            }}
          >
            이름 변경
          </button>
        )}
              <button
                type="button"
                disabled={
                  deletingAlbumId ===
                  album.id
                }
                onClick={(event) =>
                  handleDeleteAlbum(
                    event,
                    album,
                  )
                }
                style={{
                  marginTop: "16px",
                }}
              >
                {deletingAlbumId ===
                album.id
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
