const configuredUrl =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export const API_BASE_URL = configuredUrl.replace(/\/+$/, "");

const STORAGE_KEY = "memory-recall-session";
let current = readSession();

function readSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY));

    if (
      typeof value?.accessToken === "string" &&
      Number.isFinite(value?.expiresAt) &&
      value.expiresAt > Date.now()
    ) {
      return value;
    }

    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* Invalid or unavailable browser storage */
  }

  return null;
}

export function session() {
  return current;
}

export function clearSession() {
  current = null;

  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* Memory is still cleared */
  }

  window.dispatchEvent(new Event("auth-change"));
}

export function saveSession(response) {
  if (
    !response.accessToken ||
    response.tokenType !== "Bearer" ||
    !(response.expiresIn > 0)
  ) {
    throw new Error("로그인 응답을 확인할 수 없습니다.");
  }

  const payload = JSON.parse(
    atob(
      response.accessToken
        .split(".")[1]
        .replace(/-/g, "+")
        .replace(/_/g, "/"),
    ),
  );

  current = {
    accessToken: response.accessToken,
    expiresAt: Math.min(
      payload.exp * 1000,
      Date.now() + response.expiresIn * 1000,
    ),
  };

  if (!Number.isFinite(current.expiresAt)) {
    current = null;
    throw new Error("토큰 만료시간이 올바르지 않습니다.");
  }

  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {
    /* In-memory login works even if storage is disabled */
  }

  window.dispatchEvent(new Event("auth-change"));
}

export function expireSession() {
  clearSession();
  window.dispatchEvent(new Event("auth-expired"));
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || "요청을 처리하지 못했습니다.");
    this.status = status;
    this.fields = body?.fieldErrors || {};
  }
}

export async function request(
  path,
  { method = "GET", body, authenticated = true, signal } = {},
) {
  const token = authenticated ? current?.accessToken : null;

  if (authenticated && (!token || current.expiresAt <= Date.now())) {
    expireSession();

    throw new ApiError(401, {
      message: "다시 로그인해주세요.",
    });
  }

  let response;

  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      signal,
      cache: "no-store",
      credentials: "omit",
      headers: {
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;

    throw new Error(
      "서버에 연결할 수 없습니다. 네트워크와 서버 실행 상태를 확인해주세요.",
    );
  }

  const text = await response.text();
  let data = {};

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    if (response.ok) {
      throw new Error(
        "서버 응답이 JSON 형식이 아닙니다. API 서버 주소를 확인해주세요.",
      );
    }

    data = {
      message:
        "서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해주세요.",
    };
  }

  if (!response.ok) {
    if (
      response.status === 401 &&
      authenticated &&
      current?.accessToken === token
    ) {
      expireSession();
    }

    throw new ApiError(response.status, data || {});
  }

  return data;
}

async function photoImage(albumId, photoId, signal) {
  const token = current?.accessToken;

  if (!token || current.expiresAt <= Date.now()) {
    expireSession();

    throw new ApiError(401, {
      message: "다시 로그인해주세요.",
    });
  }

  let response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/albums/${encodeURIComponent(albumId)}/photos/${encodeURIComponent(photoId)}/image`,
      {
        method: "GET",
        signal,
        cache: "no-store",
        credentials: "omit",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    );
  } catch (error) {
    if (error.name === "AbortError") throw error;

    throw new Error(
      "사진을 불러오는 중 서버에 연결할 수 없습니다.",
    );
  }

  if (!response.ok) {
    if (
      response.status === 401 &&
      current?.accessToken === token
    ) {
      expireSession();
    }

    let data = null;

    try {
      data = await response.json();
    } catch {
      data = null;
    }

    throw new ApiError(
      response.status,
      data || {
        message: "사진을 불러오지 못했습니다.",
      },
    );
  }

  return response.blob();
}

async function uploadPhotos(albumId, files) {
  const token = current?.accessToken;

  if (!token || current.expiresAt <= Date.now()) {
    expireSession();

    throw new ApiError(401, {
      message: "다시 로그인해주세요.",
    });
  }

  const formData = new FormData();

  for (const file of files) {
    formData.append("files", file);
  }

  let response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/albums/${encodeURIComponent(albumId)}/photos`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      },
    );
  } catch {
    throw new Error(
      "사진 업로드 중 서버에 연결할 수 없습니다.",
    );
  }

  const text = await response.text();
  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }

  if (!response.ok) {
    if (
      response.status === 401 &&
      current?.accessToken === token
    ) {
      expireSession();
    }

    throw new ApiError(
      response.status,
      data || {
        message: "사진 업로드에 실패했습니다.",
      },
    );
  }

  return data;
}

export const api = {
  signup: (body) =>
    request("/api/auth/signup", {
      method: "POST",
      body,
      authenticated: false,
    }),

  login: (body) =>
    request("/api/auth/login", {
      method: "POST",
      body,
      authenticated: false,
    }),

  logout: () =>
    request("/api/auth/logout", {
      method: "POST",
    }),

  me: (signal) =>
    request("/api/users/me", {
      signal,
    }),

  // 앨범
  albums: (signal) =>
    request("/api/albums", {
      signal,
    }),

  createAlbum: (title) =>
    request("/api/albums", {
      method: "POST",
      body: { title },
    }),

  deleteAlbum: (albumId) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}`,
      {
        method: "DELETE",
      },
    ),

  album: (albumId, signal) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}`,
      {
        signal,
      },
    ),

  // 특정 앨범의 사진 목록
  albumPhotos: (albumId, signal) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}/photos`,
      {
        signal,
      },
    ),

  photo: (albumId, photoId, signal) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}/photos/${encodeURIComponent(photoId)}`,
      {
        signal,
      },
    ),

  uploadPhotos,
  photoImage,

  analyzePhoto: (albumId, photoId) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}/photos/${encodeURIComponent(photoId)}/analyze`,
      { method: "POST" },
    ),
  generateAlbumQuestions: (albumId) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}/questions/generate`,
      { method: "POST" },
    ),

  // 사진 삭제
  deletePhoto: (albumId, photoId) =>
    request(
      `/api/albums/${encodeURIComponent(albumId)}/photos/${encodeURIComponent(photoId)}`,
      {
        method: "DELETE",
      },
    ),

  // 전체 사용자 사진
  photos: (page = 0, signal) =>
    request(
      `/api/users/me/photos?page=${page}&size=6`,
      {
        signal,
      },
    ),

  sessions: (page = 0, signal, size = 6) =>
    request(
      `/api/users/me/training-sessions?page=${page}&size=${size}`,
      {
        signal,
      },
    ),

  detail: (id, signal) =>
    request(
      `/api/users/me/training-sessions/${encodeURIComponent(id)}`,
      {
        signal,
      },
    ),
};
