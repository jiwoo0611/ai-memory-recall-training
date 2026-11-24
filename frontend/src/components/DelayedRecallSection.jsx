import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { date, ErrorBox } from './UI';

export const recallLabels = { WAITING_TRAINING: '훈련 완료 후 예약', NOT_DUE: '평가 대기', AVAILABLE: '평가 가능', IN_PROGRESS: '작성 중', SUBMITTED: '채점 대기', GRADED: '평가 완료' };
export function RecallLink({ item }) {
  const waiting = ['WAITING_TRAINING', 'NOT_DUE'].includes(item.status);
  const days = item.availableAt ? Math.max(0, Math.ceil((new Date(item.availableAt) - Date.now()) / 86400000)) : null;
  return <article className="delayed-item">
    <div><span className="eyebrow">{recallLabels[item.status]}</span><h3>훈련 #{item.sourceSessionId}의 회상 평가</h3>
      <p className="muted">{item.availableAt ? `평가 가능 ${date(item.availableAt)}` : '훈련을 완료하면 7일 후 평가할 수 있어요.'}</p>
      {item.status === 'GRADED' && <p>7일 지연 회상률 <strong>{item.recallRate}%</strong></p>}
    </div>
    {waiting ? <button disabled>{days !== null ? `평가까지 ${days}일 남았어요` : '훈련 완료 후 예약'}</button>
      : <a className="button" href={`#/delayed-recall/${item.id}`}>{item.status === 'AVAILABLE' ? '회상 평가 시작' : item.status === 'IN_PROGRESS' ? '이어서 작성' : '결과 보기'}</a>}
  </article>;
}
export default function DelayedRecallSection({ albumId }) {
  const [items,setItems]=useState(null), [error,setError]=useState(null), [revision,setRevision]=useState(0), [review,setReview]=useState(false);
  useEffect(()=>{ const c=new AbortController(); api.delayedRecalls(c.signal, albumId).then(setItems).catch(e=>{if(!c.signal.aborted)setError(e);});
    api.delayedAccess(c.signal).then(x=>setReview(x.canReview)).catch(()=>{}); return ()=>c.abort(); },[revision,albumId]);
  return <section className="card delayed-section" aria-labelledby={albumId ? `delayed-title-${albumId}` : "delayed-title"}>
    <p className="eyebrow">다시 떠올리는 나의 기억</p><h2 id={albumId ? `delayed-title-${albumId}` : "delayed-title"}>7일 후 회상 평가</h2>
    <p className="muted">사진과 선택지 없이 기억나는 이야기를 들려주세요.</p>
    <ErrorBox error={error} retry={()=>{setError(null);setRevision(v=>v+1);}} />
    {items ? items.length ? items.map(item=><RecallLink key={item.id} item={item}/>) : <p>{albumId ? "이 앨범에는 아직 예정된 평가가 없어요. 아래에서 새 퀴즈를 시작하면 첫 문항 전에 평가 참여를 등록할 수 있어요." : "아직 예정된 평가가 없어요. 새 퀴즈의 첫 문항을 풀기 전에 평가 참여를 등록할 수 있어요."}</p> : !error && <p role="status">평가 일정을 불러오는 중…</p>}
    {review && <a className="secondary-link" href="#/recall-review">제출된 회상 평가 채점하기</a>}
  </section>;
}

export function RecallEnrollment({ sessionId, completed, canEnroll, onContinue }) {
  const [item,setItem]=useState(undefined),[error,setError]=useState(null),[text,setText]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0);
  useEffect(()=>{const c=new AbortController(); api.delayedForSession(sessionId,c.signal).then(setItem).catch(e=>{if(!c.signal.aborted)setError(e);}); return ()=>c.abort();},[sessionId,completed,revision]);
  async function enroll(e){e.preventDefault();if(busy)return;setBusy(true);setError(null);
    try {setItem(await api.enrollDelayed(sessionId,{facts:text.split('\n').map(s=>s.trim()).filter(Boolean),confirmed}));setText('');}
    catch(e){setError(e);}finally{setBusy(false);}}
  return <section className="delayed-enrollment" aria-label="7일 후 회상 평가 예약">
    <h2>7일 후 회상 평가</h2>
    <ErrorBox error={error} retry={()=>{setError(null);setRevision(v=>v+1);}}/>
    {item ? <RecallLink item={item}/> : item === null && !completed && canEnroll ? <details>
      <summary>훈련 전에 평가 참여 등록하기</summary>
      <p>이 경험에서 실제로 확인한 사실을 한 줄에 하나씩 적어 주세요. 등록한 사실은 7일 후 채점 기준이 되며 변경할 수 없어요. AI의 추측은 적지 마세요.</p>
      <form onSubmit={enroll}><label htmlFor="recall-facts">확인된 사실 목록</label>
        <textarea id="recall-facts" rows={5} maxLength={15030} value={text} onChange={e=>setText(e.target.value)} required disabled={busy}/>
        <p className="hint">한 줄당 500자 이내, 최대 30개. 같은 사실을 중복해서 적지 마세요.</p>
        <label className="delayed-check"><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)} required disabled={busy}/>사진 소유자로서 위 사실이 실제 경험과 일치함을 확인했어요.</label>
        <button className="button" disabled={busy||!confirmed||!text.trim()}>{busy?'등록 중…':'사실 확인하고 평가 예약'}</button>
      </form></details> : item === null ? <p className="muted">이 훈련에는 평가가 등록되지 않았어요. 새 훈련의 첫 문항을 풀기 전에 참여를 등록해 주세요.</p> : !error && <p role="status">평가 예약을 확인하는 중…</p>}
    {onContinue && (item !== undefined || error) && <button className="button" type="button" disabled={busy} onClick={onContinue}>{item ? '훈련 시작' : '평가 없이 훈련 시작'}</button>}
  </section>;
}

