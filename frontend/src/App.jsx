import React, { useEffect, useState } from "react";
import { api, session, clearSession, expireSession } from "./api/client";
import Home from "./pages/Home";
import AuthPage from "./pages/AuthPage";
import MyPage from "./pages/MyPage";
import PhotoDetailPage from "./pages/PhotoDetailPage";
import RecallPage from "./pages/RecallPage";
import { ErrorBox } from "./components/UI";
const route = () => location.hash.slice(1).split("?")[0] || "/";
// Authentication redirects replace the current entry; ordinary links keep history.
const redirect = (path) => {
  if (location.hash !== `#${path}`) location.replace(`#${path}`);
};
export default function App() {
  const [page, setPage] = useState(route);
  const [auth, setAuth] = useState(session);
  const [notice, setNotice] = useState("");
  const [logoutError, setLogoutError] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const albumRoute = page.match(/^\/albums\/([^/]+)$/);
  const trainingRoute = page.match(/^\/training\/([^/]+)$/);
  const quizRoute = page.match(/^\/albums\/([^/]+)\/quiz\/([^/]+)$/);
  const photoRoute = page.match(
    /^\/albums\/([^/]+)\/photos\/([^/]+)$/,
  );
  useEffect(() => {
    const navigate = () => {
      setPage(route());
      setLogoutError(null);
      window.scrollTo(0, 0);
    };
    const sync = () => setAuth(session());
    const expired = () => {
      setNotice(
        "로그인이 만료되었거나 인증이 해제되었습니다. 다시 로그인해주세요.",
      );
      redirect("/login");
    };
    window.addEventListener("hashchange", navigate);
    window.addEventListener("auth-change", sync);
    window.addEventListener("auth-expired", expired);
    return () => {
      window.removeEventListener("hashchange", navigate);
      window.removeEventListener("auth-change", sync);
      window.removeEventListener("auth-expired", expired);
    };
  }, []);
  useEffect(() => {
    if (!auth) return;
    const check = () => {
      if (session()?.expiresAt <= Date.now()) expireSession();
    };
    const timer = setTimeout(check, Math.max(0, auth.expiresAt - Date.now()));
    window.addEventListener("focus", check);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("focus", check);
    };
  }, [auth]);
  useEffect(() => {
    if ((page === "/mypage" || albumRoute || quizRoute || photoRoute || trainingRoute) && !auth) {
      setNotice("내 기록을 확인하려면 로그인해주세요.");
      redirect("/login");
    }
  }, [page, auth]);
  async function logout() {
    setLoggingOut(true);
    setLogoutError(null);
    try {
      await api.logout();
      clearSession();
      setNotice("안전하게 로그아웃되었습니다.");
      redirect("/login");
    } catch (e) {
      if (e.status !== 401)
        setLogoutError(
          new Error(
            `${e.message} 로그아웃이 완료되지 않았습니다. 다시 시도해주세요.`,
          ),
        );
    } finally {
      setLoggingOut(false);
    }
  }
  let content;
  if (page === "/") content = <Home signedIn={!!auth} />;
  else if (page === "/signup")
    content = (
      <AuthPage
        key="signup"
        signup
        onSuccess={(message) => {
          setNotice(message);
          redirect("/login");
        }}
      />
    );
  else if (page === "/login")
    content = (
      <AuthPage
        key="login"
        notice={notice}
        onSuccess={() => {
          setNotice("");
          redirect("/mypage");
        }}
      />
    );
  else if (page === "/mypage" || albumRoute)
    content = auth ? (
      <MyPage
        key={auth.accessToken}
        albumId={albumRoute ? decodeURIComponent(albumRoute[1]) : undefined}
      />
    ) : (
      <p role="status">로그인 화면으로 이동합니다…</p>
    );
  else if (trainingRoute)
    content = auth ? <RecallPage key={`${auth.accessToken}-${trainingRoute[1]}`} sessionId={decodeURIComponent(trainingRoute[1])} /> : <p role="status">로그인 화면으로 이동합니다…</p>;
  else if (quizRoute)
    content = auth ? (
      <RecallPage
        key={`${auth.accessToken}-${quizRoute[2]}`}
        albumId={decodeURIComponent(quizRoute[1])}
        sessionId={decodeURIComponent(quizRoute[2])}
      />
    ) : (
      <p role="status">로그인 화면으로 이동합니다…</p>
    );
  else if (photoRoute)
    content = auth ? (
      <PhotoDetailPage
        albumId={decodeURIComponent(photoRoute[1])}
        photoId={decodeURIComponent(photoRoute[2])}
      />
    ) : (
      <p role="status">로그인 화면으로 이동합니다…</p>
    );
  else
    content = (
      <section className="card">
        <h1>페이지를 찾을 수 없습니다.</h1>
        <a href="#/">홈으로 돌아가기</a>
      </section>
    );
  return (
    <>
      <a
        className="skip"
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main").focus();
        }}
      >
        본문 바로가기
      </a>
      <header>
        <a className="brand" href="#/">
          <span className="brand-icon" aria-hidden="true">
            ▧
          </span>{" "}
          기억산책
        </a>
        <nav aria-label="주 메뉴">
          <a href="#/" aria-current={page === "/" ? "page" : undefined}>
            홈
          </a>
          {auth ? (
            <>
              <a
                href="#/mypage"
                aria-current={page === "/mypage" || albumRoute || quizRoute || photoRoute ? "page" : undefined}
              >
                마이페이지
              </a>
              <button onClick={logout} disabled={loggingOut}>
                {loggingOut ? "로그아웃 중…" : "로그아웃"}
              </button>
            </>
          ) : (
            <>
              <a
                href="#/login"
                aria-current={page === "/login" ? "page" : undefined}
              >
                로그인
              </a>
              <a className="nav-cta" href="#/signup">
                회원가입
              </a>
            </>
          )}
        </nav>
      </header>
      <main id="main" tabIndex={-1}>
        <ErrorBox error={logoutError} />
        {content}
      </main>
      <footer>
        <span className="brand">기억산책</span>
        <p>소중한 순간을 나의 이야기로.</p>
        <small>사진 기반 기억 회상 및 인지 지원 서비스</small>
      </footer>
    </>
  );
}
