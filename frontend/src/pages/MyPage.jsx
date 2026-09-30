import React, { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import {
  Badge,
  date,
  Empty,
  ErrorBox,
  Pager,
  labels,
} from "../components/UI";
import AlbumSection from "../components/AlbumSection";

function useLoad(loader, dependency) {
  const [state, setState] = useState({ loading: true });
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    setState({ loading: true });

    loader(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setState({
            data,
            loading: false,
          });
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setState({
            error,
            loading: false,
          });
        }
      });

    return () => controller.abort();
  }, [dependency, revision]);

  return {
    ...state,
    retry: () => setRevision((x) => x + 1),
  };
}

function LoadState({ state, children }) {
  if (state.loading) {
    return (
      <p role="status" className="loading">
        기록을 불러오는 중입니다…
      </p>
    );
  }

  if (state.error) {
    return (
      <ErrorBox
        error={state.error}
        retry={state.retry}
      />
    );
  }

  return children(state.data);
}

function GrowthStats({ state }) {
  return <LoadState state={state}>{(data) => {
    const rows = data.content.filter(x => x.result?.correctCount != null).slice().reverse();
    const total = rows.reduce((n, x) => n + x.result.totalCount, 0);
    const correct = rows.reduce((n, x) => n + x.result.correctCount, 0);
    const rate = total ? Math.round(correct / total * 100) : 0;
    const level = Math.max(1, Math.floor(correct / 10) + 1);
    const progress = (correct % 10) * 10;
    const stages = ["🌱", "🌿", "🌳", "✨🌳"];
    return <section className="card growth-stats" aria-labelledby="growth-title">
      <div className="section-heading"><div><p className="eyebrow">MY TRAINING JOURNEY</p><h2 id="growth-title">나의 성장 기록</h2></div><span className="growth-level">Lv. {level} {stages[Math.min(stages.length - 1, Math.floor((level - 1) / 3))]}</span></div>
      <p className="muted">저장된 훈련 기록 기준으로 계산한 통계입니다.</p>
      <div className="growth-metrics"><strong>평균 정답률 {rate}%</strong><span>총 {rows.length}회 훈련 · {correct}문항 정답</span></div>
      <div className="growth-progress" role="progressbar" aria-valuenow={progress} aria-valuemin="0" aria-valuemax="100"><span style={{width: `${progress}%`}} /></div>
      <p className="muted">다음 레벨까지 {10 - (correct % 10)}문항 남았습니다.</p>
      {rows.length ? <div className="growth-chart" aria-label="날짜별 정답률">{rows.slice(-7).map(item => <div className="growth-bar" key={item.id}><span style={{height: `${Math.max(8, item.result.correctCount / item.result.totalCount * 100)}%`}} /><small>{new Date(item.startedAt).toLocaleDateString("ko-KR", {month:"numeric", day:"numeric"})}</small></div>)}</div> : <Empty>결과가 저장되면 성장 기록이 시작됩니다.</Empty>}
    </section>;
  }}</LoadState>;
}

function SessionDetail({ id, onClose }) {
  const section = useRef(null);

  useEffect(() => {
    section.current?.focus();
  }, [id]);

  const state = useLoad(
    (signal) => api.detail(id, signal),
    id,
  );

  return (
    <section
      ref={section}
      tabIndex={-1}
      className="card detail"
      aria-label="훈련 상세"
    >
      <div className="section-heading">
        <h2>
          훈련 상세{" "}
          <span className="muted">
            #{id}
          </span>
        </h2>

        <button
          type="button"
          onClick={onClose}
        >
          상세 닫기
        </button>
      </div>

      <LoadState state={state}>
        {(data) => (
          <>
            <div className="detail-heading">
              <h3>
                {data.photo.originalFilename}
              </h3>

              <Badge value={data.status} />
            </div>

            <p className="muted">
              사진 #{data.photo.id} · 시작{" "}
              {date(data.startedAt)} · 완료{" "}
              {date(data.completedAt)}
            </p>

            <h3>질문과 답변</h3>
            <p><a className="button" href={`#/training/${data.id}`}>{data.status === "COMPLETED" ? "저장된 결과 보기" : "답변 작성·훈련 열기"}</a></p>

            {!data.questions.length ? (
              <Empty>
                아직 질문과 답변이 없습니다.
              </Empty>
            ) : (
              data.questions.map((q) => (
                <article
                  className="question"
                  key={q.id}
                >
                  <p className="eyebrow">
                    질문 {q.questionOrder} ·{" "}
                    {labels[q.type] || q.type}
                  </p>

                  <h4>{q.content}</h4>

                  {q.answer ? (
                    <div className="answer">
                      <Badge
                        value={q.answer.status}
                      />

                      <p>
                        {q.answer.content ||
                          labels[
                            q.answer.status
                          ]}
                      </p>
                      {q.answer.correct != null && <p>{q.answer.correct ? "정답" : "오답"} · 정답: {q.answer.correctOption}</p>}

                      <small className="muted">
                        {date(
                          q.answer.createdAt,
                        )}
                      </small>
                    </div>
                  ) : (
                    <p className="muted">
                      아직 답변이 없습니다.
                    </p>
                  )}
                </article>
              ))
            )}

            <h3>훈련 결과</h3>

            {data.result ? (
              <div className="result">
                <Badge
                  value={
                    data.result
                      .feedbackStatus
                  }
                />

                <p>
                  {data.result.summary ||
                    "아직 결과 요약이 없습니다."}
                </p>

                <div className="result-counts">
                  {data.result.correctCount != null && <><span>정답 {data.result.correctCount} / {data.result.totalCount}</span><span>오답 {data.result.incorrectCount}</span></>}
                  <span>
                    답변{" "}
                    {
                      data.result
                        .answeredCount
                    }
                  </span>

                  <span>
                    기억나지 않음{" "}
                    {
                      data.result
                        .cannotRecallCount
                    }
                  </span>

                  <span>
                    질문 불일치{" "}
                    {
                      data.result
                        .mismatchCount
                    }
                  </span>
                </div>
              </div>
            ) : (
              <Empty>
                아직 훈련 결과가 없습니다.
              </Empty>
            )}
          </>
        )}
      </LoadState>
    </section>
  );
}

export default function MyPage({ albumId }) {
  const [trainingPage, setTrainingPage] =
    useState(0);

  const [selected, setSelected] =
    useState(null);
  const [photoFilter, setPhotoFilter] = useState(null);

  const profile = useLoad(
    api.me,
    "profile",
  );

  const trainings = useLoad(
    (signal) =>
      photoFilter ? api.photoSessions(photoFilter, trainingPage, signal) : api.sessions(
        trainingPage,
        signal,
      ),
    `${trainingPage}-${photoFilter}`,
  );

  const recent = useLoad(
    async (signal) => {
      const [p, t] =
        await Promise.all([
          api.photos(0, signal),
          api.sessions(0, signal),
        ]);

      return [
        ...p.content.map((x) => ({
          key: `p${x.id}`,
          type: "사진 등록",
          title:
            x.originalFilename,
          time: x.createdAt,
        })),

        ...t.content.map((x) => ({
          key: `t${x.id}`,
          type: "훈련 시작",
          title:
            x.photo
              .originalFilename,
          time: x.startedAt,
        })),
      ]
        .sort(
          (a, b) =>
            new Date(b.time) -
            new Date(a.time),
        )
        .slice(0, 6);
    },
    "recent",
  );
  const stats = useLoad((signal) => api.sessions(0, signal, 100), "growth-stats");

  return (
    <>
      <div className="page-title">
        <p className="eyebrow">
          MY MEMORY
        </p>

        <h1>나의 기억 보관함</h1>

        <p className="muted">
          사진 한 장에서 시작된
          이야기를 다시 만나보세요.
        </p>
      </div>

      <section className="card profile">
        <LoadState state={profile}>
          {(user) => (
            <>
              <div
                className="avatar"
                aria-hidden="true"
              >
                {user.name.slice(
                  0,
                  1,
                )}
              </div>

              <div>
                <h2>
                  {user.name}님,
                  반갑습니다.
                </h2>

                <p className="muted">
                  {user.email}
                </p>
              </div>

              <dl>
                <div>
                  <dt>사용자 ID</dt>
                  <dd>{user.id}</dd>
                </div>

                <div>
                  <dt>가입일</dt>
                  <dd>
                    {date(
                      user.createdAt,
                    )}
                  </dd>
                </div>

                <div>
                  <dt>
                    정보 수정일
                  </dt>
                  <dd>
                    {date(
                      user.updatedAt,
                    )}
                  </dd>
                </div>
              </dl>
            </>
          )}
        </LoadState>
      </section>
      <GrowthStats state={stats} />

      <div className="dashboard">
        <div className="main-column">

          {/* 앨범 + 사진 관리 */}
          <AlbumSection key={albumId ?? "albums"} albumId={albumId} />

          {/* 회상 훈련 기록 */}
          <section className="card">
            <div className="section-heading">
              <h2>
                회상 훈련 기록{" "}
                <span className="count">
                  {trainings.data
                    ?.totalElements ??
                    "—"}
                </span>
              </h2>
            </div>

            {photoFilter && <p>사진 #{photoFilter}의 기록 <button onClick={() => { setPhotoFilter(null); setTrainingPage(0); }}>전체 기록 보기</button></p>}
            <LoadState
              state={trainings}
            >
              {(data) => (
                <>
                  {!data.content
                    .length ? (
                    <Empty />
                  ) : (
                    <div className="training-list">
                      {data.content.map(
                        (s) => (
                          <article
                            className="training-item"
                            key={s.id}
                          >
                            <div>
                              <Badge
                                value={
                                  s.status
                                }
                              />

                              <h3>
                                {
                                  s
                                    .photo
                                    .originalFilename
                                }
                              </h3>

                              <p className="muted">
                                훈련 #
                                {s.id} ·
                                사진 #
                                {
                                  s
                                    .photo
                                    .id
                                }
                              </p>

                              <p className="muted">
                                시작{" "}
                                {date(
                                  s.startedAt,
                                )}
                                <br />
                                완료{" "}
                                {date(
                                  s.completedAt,
                                )}
                              </p>
                              {s.result?.correctCount != null && <p>정답 {s.result.correctCount} / {s.result.totalCount} · 오답 {s.result.incorrectCount}</p>}
                              <button onClick={() => { setPhotoFilter(s.photo.id); setTrainingPage(0); setSelected(null); }}>같은 사진의 기록</button>
                            </div>

                            <button
                              type="button"
                              aria-label={`훈련 ${s.id} 상세 보기`}
                              aria-expanded={
                                selected ===
                                s.id
                              }
                              onClick={() =>
                                setSelected(
                                  s.id,
                                )
                              }
                            >
                              상세 보기{" "}
                              <span
                                aria-hidden="true"
                              >
                                ↗
                              </span>
                            </button>
                          </article>
                        ),
                      )}
                    </div>
                  )}

                  <Pager
                    data={data}
                    onPage={(
                      page,
                    ) => {
                      setTrainingPage(
                        page,
                      );

                      setSelected(
                        null,
                      );
                    }}
                  />
                </>
              )}
            </LoadState>
          </section>

          {selected !== null && (
            <SessionDetail
              key={selected}
              id={selected}
              onClose={() =>
                setSelected(null)
              }
            />
          )}
        </div>

        {/* 최근 활동 */}
        <aside className="card activity">
          <h2>최근 활동</h2>

          <p className="hint">
            최신 사진 등록·훈련 시작
            기록
          </p>

          <LoadState state={recent}>
            {(items) =>
              items.length ? (
                <ol>
                  {items.map(
                    (item) => (
                      <li
                        key={
                          item.key
                        }
                      >
                        <span className="eyebrow">
                          {
                            item.type
                          }
                        </span>

                        <p>
                          {
                            item.title
                          }
                        </p>

                        <small className="muted">
                          {date(
                            item.time,
                          )}
                        </small>
                      </li>
                    ),
                  )}
                </ol>
              ) : (
                <Empty>
                  아직 활동 기록이
                  없습니다.
                </Empty>
              )
            }
          </LoadState>
        </aside>
      </div>
    </>
  );
}
