import React, { useEffect, useState } from "react";
import { api } from "../api/client";

export default function PhotoImage({
  albumId,
  photo,
  variant = "thumbnail",
}) {
  const [imageUrl, setImageUrl] = useState("");
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let objectUrl = "";

    async function loadImage() {
      try {
        setImageError(false);
        setImageUrl("");

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

  const className = `photo-image photo-image-${variant}`;

  if (imageError) {
    return (
      <div
        className={`${className} photo-image-placeholder`}
        role="img"
        aria-label={`${photo.originalFilename} 이미지를 불러올 수 없음`}
      >
        <span aria-hidden="true">▧</span>
        {variant === "detail" && (
          <p>원본 사진을 불러올 수 없습니다.</p>
        )}
      </div>
    );
  }

  if (!imageUrl) {
    return (
      <div
        className={`${className} photo-image-placeholder`}
        role="status"
      >
        사진을 불러오는 중...
      </div>
    );
  }

  return (
    <img
      className={className}
      src={imageUrl}
      alt={photo.originalFilename}
    />
  );
}
