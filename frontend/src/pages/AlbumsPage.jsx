import React from "react";
import AlbumSection from "../components/AlbumSection";

export default function AlbumsPage({ albumId }) {
  return (
    <div className="photo-library">
      <div className="page-title">
        <p className="eyebrow">MY PHOTO LIBRARY</p>
        <h1>사진 보관함</h1>
        <p className="muted">소중한 사진을 앨범으로 모으고, 회상 훈련을 시작해 보세요.</p>
      </div>
      <AlbumSection key={albumId ?? "albums"} albumId={albumId} />
    </div>
  );
}
