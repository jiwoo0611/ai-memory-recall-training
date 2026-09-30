import React, { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { Badge, date, Empty, ErrorBox, Pager } from "../components/UI";
import PhotoImage from "../components/PhotoImage";
import QuizPage from "./QuizPage";

const states = { ANSWERED: "직접 답변", CANNOT_RECALL: "기억나지 않아요", QUESTION_MISMATCH: "질문이 사진과 맞지 않아요" };

export default function RecallPage({ sessionId, albumId }) {
  const [data, setData] = useState(null);
  const [draft, setDraft] = useState({});
  const [error, setError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState("");
  const [history, setHistory] = useState(null);
  const [historyError, setHistoryError] = useState(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyRevision, setHistoryRevision] = useState(0);
  const lock = useRef(false);
  const retryKey = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    setData(null); setError(null);
    api.detail(sessionId, controller.signal).then((value) => {
      if (controller.signal.aborted) return;
      if (albumId && String(value.photo.albumId) !== String(albumId)) throw new Error("이 앨범의 훈련이 아닙니다.");
      setData(value);
    }).catch((e) => { if (!controller.signal.aborted) setError(e); });
    return () => controller.abort();
  }, [sessionId, albumId, revision]);

  useEffect(() => {
    if (!data) return;
    const controller = new AbortController();
    setHistory(null); setHistoryError(null);
    api.photoSessions(data.photo.id, historyPage, controller.signal).then((value) => {
      if (!controller.signal.aborted) setHistory(value);
    }).catch((e) => { if (!controller.signal.aborted) setHistoryError(e); });
    return () => controller.abort();
  }, [data?.photo.id, historyPage, historyRevision]);

  async function submit(event) {
    event.preventDefault();
    if (lock.current) return;
    const items = data.questions.map((q) => ({ questionId: q.id, ...draft[q.id] }));
    if (items.some((a) => !a.status || (a.status === "ANSWERED" && (!a.content?.trim() || a.content.length > 2000)))) {
      setActionError(new Error("모든 질문에 1~2000자의 답변을 입력하거나 건너뛰기 상태를 선택해주세요.")); return;
    }
    lock.current = true; setBusy("submit"); setActionError(null);
    try {
      const saved = await api.submitRecall(sessionId, items.map((a) => ({ ...a, content: a.status === "ANSWERED" ? a.content.trim() : null })));
      setData(saved); setDraft({}); setHistoryRevision((v) => v + 1);
      window.scrollTo(0, 0);
      setBusy("feedback");
      try { setData(await api.feedback(sessionId)); }
      catch (e) { setActionError(new Error(`답변은 저장되었습니다. 요약 생성만 다시 시도해주세요. ${e.message}`)); }
    } catch (e) { setActionError(e); }
    finally { lock.current = false; setBusy(""); }
  }

  async function generateFeedback() {
    if (lock.current) return;
    lock.current = true; setBusy("feedback"); setActionError(null);
    try { setData(await api.feedback(sessionId)); }
    catch (e) { setActionError(new Error(`답변은 저장되어 있습니다. ${e.message}`)); }
    finally { lock.current = false; setBusy(""); }
  }

  async function retrain() {
    if (lock.current) return;
    lock.current = true; setBusy("retrain"); setActionError(null);
    // Keep the identity through network failure so retries cannot create extra sessions.
    retryKey.current ||= crypto.randomUUID();
    try {
      const result = await api.retrain(sessionId, retryKey.current);
      location.hash = `/training/${result.sessionId}`;
    } catch (e) { setActionError(e); }
    finally { lock.current = false; setBusy(""); }
  }

  if (error) return <section className="card"><ErrorBox error={error} retry={() => setRevision((v) => v + 1)} /></section>;
  if (!data) return <p role="status">훈련 기록을 불러오는 중입니다…</p>;
  // Preserve legacy free-text records; new multiple-choice sessions use the quiz flow.
  if (data.result?.correctCount != null || (data.status !== "COMPLETED" && data.questions.length > 0 && data.questions.every(q => q.format === "MULTIPLE_CHOICE"))) {
    return <QuizPage key={sessionId} sessionId={sessionId} albumId={data.photo.albumId} />;
  }
  const completed = data.status === "COMPLETED";
  const selected = data.questions.filter((q) => draft[q.id]?.status &&
    (draft[q.id].status !== "ANSWERED" || draft[q.id].content?.trim())).length;
  const result = data.result;

  return <div className="recall-layout">
    <section className="card recall-main">
      <a href="#/mypage" className="back-link">← 내 훈련 이력</a>
      <p className="eyebrow">나의 회상 기록</p>
      <h1>{completed ? "훈련 결과" : "사진 속 기억을 들려주세요"}</h1>
      <p className="muted">시작 {date(data.startedAt)}{completed && ` · 완료 ${date(data.completedAt)}`}</p>
      <Badge value={data.status} />
      {data.photo.albumId && (completed || data.questions.every((q) => q.format !== "MULTIPLE_CHOICE")) && <div className="recall-photo"><PhotoImage albumId={data.photo.albumId} photo={data.photo} variant="detail" /></div>}
      {completed ? <>
        <p className="recall-notice" role="status">답변 저장이 완료되었습니다. 다시 로그인해도 이 기록을 확인할 수 있습니다.</p>
        <div className="result-counts">
          <span>응답 {data.questions.filter((q) => q.answer).length} / {data.questions.length}문항</span>
          <span>직접 답변 {result?.answeredCount ?? 0}</span>
          <span>기억나지 않음 {result?.cannotRecallCount ?? 0}</span>
          <span>질문 불일치 {result?.mismatchCount ?? 0}</span>
        </div>
        <h2>질문별 답변</h2>
        {data.questions.map((q) => <article className="question" key={q.id}>
          <p className="eyebrow">질문 {q.questionOrder}</p><h3>{q.content}</h3>
          {q.answer ? <><Badge value={q.answer.status} /><p className="recall-text">{q.answer.content || states[q.answer.status]}</p></>
            : <p className="muted">아직 답하지 않았어요.</p>}
        </article>)}
        <section className="recall-feedback" aria-label="회상 요약">
          <h2>회상 요약</h2>
          {result?.feedbackStatus === "COMPLETED" ? <p className="recall-text">{result.summary}</p> : <>
            <p>{result?.feedbackStatus === "FAILED" ? "요약 생성에 실패했습니다. 저장된 답변은 위에서 확인할 수 있습니다." : "답변은 저장되었고 회상 요약은 생성 대기 중입니다."}</p>
            <button disabled={!!busy} onClick={generateFeedback}>{busy === "feedback" ? "요약 생성 중…" : "요약 생성·재시도"}</button>
          </>}
        </section>
        <ErrorBox error={actionError} />
        <button className="button" onClick={retrain} disabled={!!busy}>{busy === "retrain" ? "새 훈련 준비 중…" : "같은 사진으로 새 훈련"}</button>
        <p className="hint">이전 기록을 유지하고 같은 질문으로 새 훈련을 시작합니다.</p>
      </> : data.status !== "IN_PROGRESS" ? <Empty>질문 준비가 완료되면 답변할 수 있습니다.</Empty> : <form onSubmit={submit}>
        <p>정답을 맞히려 애쓰지 않아도 괜찮아요. 떠오르는 내용을 적거나 아래 상태를 선택해주세요.</p>
        <p role="status">{selected} / {data.questions.length}문항 응답 · 선택하지 않은 질문은 미응답입니다.</p>
        {data.questions.map((q) => {
          const value = draft[q.id] || {};
          const update = (change) => setDraft((old) => ({ ...old, [q.id]: { ...old[q.id], ...change } }));
          return <fieldset key={q.id} className="recall-question" disabled={!!busy}>
            <legend>{q.questionOrder}. {q.content}</legend>
            {q.format === "MULTIPLE_CHOICE" && (q.displayPhotoIds?.length ? <div className="recall-clues">
              {q.displayPhotoIds.map((id) => <PhotoImage key={id} albumId={data.photo.albumId} photo={{ id, originalFilename: `질문 ${q.questionOrder}의 단서 사진 ${id}` }} variant="quiz" />)}
            </div> : <p className="hint">이 질문은 사진 없이 기억을 떠올려보세요.</p>)}
            <div className="recall-states">{Object.entries(states).map(([status, label]) => <label key={status}>
              <input type="radio" name={`status-${q.id}`} checked={value.status === status} onChange={() => update({ status })} />{label}
            </label>)}</div>
            {value.status === "ANSWERED" && <>
              <label htmlFor={`answer-${q.id}`}>떠오르는 기억</label>
              <textarea id={`answer-${q.id}`} rows={4} maxLength={2000} value={value.content || ""}
                onChange={(e) => update({ content: e.target.value })} aria-describedby={`count-${q.id}`} />
              <small id={`count-${q.id}`}>{value.content?.length || 0} / 2000자</small>
              {!!q.options?.length && <div className="recall-options"><p className="hint">기존 질문의 보기를 참고해 답변할 수도 있어요.</p>
                {q.options.map((option, index) => <button type="button" key={index} onClick={() => update({ content: option })}>{option}</button>)}
              </div>}
            </>}
          </fieldset>;
        })}
        <ErrorBox error={actionError} />
        {actionError && <p>입력한 내용은 유지되어 있습니다. 연결을 확인한 뒤 다시 제출해주세요.</p>}
        <button className="button" disabled={!!busy || !data.questions.length} type="submit">{busy === "submit" ? "답변 저장 중…" : "제출하고 결과 보기"}</button>
      </form>}
    </section>
    <aside className="card recall-history"><h2>같은 사진의 훈련</h2>
      <ErrorBox error={historyError} retry={() => setHistoryRevision((v) => v + 1)} />
      {history ? <>{history.content.length ? history.content.map((item) => <a className="recall-history-item" key={item.id} href={`#/training/${item.id}`} aria-current={String(item.id) === String(sessionId) ? "page" : undefined}>
        <span>{date(item.startedAt)}</span><Badge value={item.status} /><small>훈련 #{item.id}</small>
      </a>) : <Empty>이 사진의 기록이 없습니다.</Empty>}<Pager data={history} onPage={setHistoryPage} /></> : !historyError && <p role="status">이력을 불러오는 중…</p>}
    </aside>
  </div>;
}
