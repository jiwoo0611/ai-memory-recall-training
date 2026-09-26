import React from "react";
export default function Home({ signedIn }) {
  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">나의 기억을 천천히, 오래도록</p>
          <h1>
            사진에 담긴 순간,
            <br />
            나의 이야기로 이어가요.
          </h1>
          <p className="hero-copy">
            소중한 사진과 회상 기록을 한곳에서 만나보세요.
            <br />
            기억산책이 당신의 이야기를 함께 간직합니다.
          </p>
          <a className="button" href={signedIn ? "#/mypage" : "#/login"}>
            {signedIn ? "내 기록 살펴보기" : "로그인하고 시작하기"}{" "}
            <span aria-hidden="true">↗</span>
          </a>
          <a
            className="secondary-link"
            href={signedIn ? "#/mypage" : "#/signup"}
          >
            {signedIn ? "마이페이지로 이동" : "처음이라면 회원가입"}
          </a>
        </div>
        <div className="memory-art" aria-hidden="true">
          <div className="art-circle" />
          <div className="photo-frame">
            <div className="landscape">
              <div className="sun" />
              <div className="hill one" />
              <div className="hill two" />
            </div>
            <span>오래 간직하고 싶은 순간</span>
          </div>
          <div className="art-note">
            하나의 사진,
            <br />
            여러 번의 기억.
          </div>
        </div>
      </section>
      <section className="features">
        <article>
          <span className="step">01 / 사진</span>
          <h2>순간을 돌아보고</h2>
          <p>내 계정에 저장된 사진 정보를 차분히 살펴보세요.</p>
        </article>
        <article>
          <span className="step">02 / 회상</span>
          <h2>이야기를 되짚고</h2>
          <p>훈련에서 나눈 질문과 나의 답변을 다시 읽어보세요.</p>
        </article>
        <article>
          <span className="step">03 / 기록</span>
          <h2>기억을 이어가요</h2>
          <p>이전 훈련의 결과와 최근 기록을 모아볼 수 있어요.</p>
        </article>
      </section>
    </>
  );
}
