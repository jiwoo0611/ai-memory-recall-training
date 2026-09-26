import React, { useState } from "react";
import { api, saveSession } from "../api/client";
import { ErrorBox } from "../components/UI";
export default function AuthPage({ signup = false, notice, onSuccess }) {
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    setError(null);
    const form = new FormData(event.currentTarget);
    const body = {
      email: form.get("email").trim().toLowerCase(),
      password: form.get("password"),
    };
    if (signup) body.name = form.get("name").trim();
    if (new TextEncoder().encode(body.password).length > 72) {
      setError(
        new Error(
          "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다. 한글은 보통 한 글자당 3바이트입니다.",
        ),
      );
      return;
    }
    if (signup && !body.name) {
      setError(new Error("이름을 입력해주세요."));
      return;
    }
    setBusy(true);
    try {
      if (signup) {
        await api.signup(body);
        onSuccess("가입이 완료되었습니다. 로그인해주세요.");
      } else {
        saveSession(await api.login(body));
        onSuccess();
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="auth-layout">
      <div className="auth-intro">
        <p className="eyebrow">MEMORY WALK</p>
        <h1>
          {signup ? "새로운 기억산책을\n시작해보세요." : "다시 만나 반가워요."}
        </h1>
        <p>
          나만의 사진과 이야기,
          <br />
          나만의 공간에서 안전하게 만나보세요.
        </p>
        <div className="intro-line" />
      </div>
      <div className="card auth-card">
        <h2>{signup ? "회원가입" : "로그인"}</h2>
        <p className="muted">
          {signup
            ? "아래 정보를 입력해 계정을 만들어주세요."
            : "가입한 이메일로 로그인해주세요."}
        </p>
        {notice && (
          <p role="status" className="notice">
            {notice}
          </p>
        )}
        <ErrorBox error={error} />
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            {signup && (
              <label>
                이름
                <input
                  name="name"
                  autoComplete="name"
                  required
                  maxLength={100}
                  placeholder="이름을 입력해주세요"
                />
                {error?.fields?.name && <small>{error.fields.name}</small>}
              </label>
            )}
            <label>
              이메일
              <input
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={254}
                placeholder="name@example.com"
              />
              {error?.fields?.email && <small>{error.fields.email}</small>}
            </label>
            <label>
              비밀번호
              <input
                name="password"
                type="password"
                autoComplete={signup ? "new-password" : "current-password"}
                required
                minLength={signup ? 8 : 1}
                maxLength={72}
                placeholder={
                  signup ? "8자 이상 입력해주세요" : "비밀번호를 입력해주세요"
                }
              />
              {error?.fields?.password && (
                <small>{error.fields.password}</small>
              )}
            </label>
            {signup && (
              <p className="hint">
                비밀번호는 8~72자, UTF-8 기준 최대 72바이트입니다.
              </p>
            )}
            <button className="button full" type="submit">
              {busy ? "처리 중…" : signup ? "회원가입" : "로그인"}
            </button>
          </fieldset>
        </form>
        <p className="auth-switch">
          {signup ? "이미 계정이 있나요?" : "아직 계정이 없나요?"}{" "}
          <a href={signup ? "#/login" : "#/signup"}>
            {signup ? "로그인" : "회원가입"}
          </a>
        </p>
      </div>
    </section>
  );
}
