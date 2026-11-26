import { trainingName } from "../api/trainingName";
import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { date, ErrorBox } from './UI';
import PhotoImage from './PhotoImage';

export const recallLabels = { WAITING_TRAINING: '훈련 완료 후 예약', NOT_DUE: '평가 대기', AVAILABLE: '평가 가능', IN_PROGRESS: '작성 중', SUBMITTED: 'AI 채점 대기', GRADED: 'AI 채점 완료' };
export function RecallLink({ item }) {
  const waiting = ['WAITING_TRAINING', 'NOT_DUE'].includes(item.status);
  const days = item.availableAt ? Math.max(0, Math.ceil((new Date(item.availableAt) - Date.now()) / 86400000)) : null;
  return <article className="delayed-item">
    <div><span className="eyebrow">{recallLabels[item.status]}</span><h3>{trainingName(item)}의 회상 평가</h3>
      <p className="muted">{item.availableAt ? `평가 가능 ${date(item.availableAt)}` : '훈련을 완료하면 7일 후 평가할 수 있어요.'}</p>
      {item.status === 'GRADED' && <p>7일 지연 회상률 <strong>{item.recallRate}%</strong></p>}
    </div>
    {waiting ? <button disabled>{days !== null ? `평가까지 ${days}일 남았어요` : '훈련 완료 후 예약'}</button>
      : <a className="button" href={`#/delayed-recall/${item.id}`}>{item.status === 'AVAILABLE' ? '회상 평가 시작' : item.status === 'IN_PROGRESS' ? '이어서 작성' : item.status == 'SUBMITTED' ? '채점 확인' : '결과 보기'}</a>}
  </article>;
}
export default function DelayedRecallSection({ albumId }) {
  const [items,setItems]=useState(null), [error,setError]=useState(null), [revision,setRevision]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();
    setItems(null); setError(null);
    api.delayedRecalls(controller.signal,albumId).then(value=>{if(!controller.signal.aborted)setItems(value);})
      .catch(error=>{if(!controller.signal.aborted)setError(error);});
    return ()=>controller.abort();
  },[revision,albumId]);
  const titleId=albumId ? `delayed-title-${albumId}` : 'delayed-title';
  return <section className="card delayed-section" aria-labelledby={titleId}>
    <p className="eyebrow">다시 떠올리는 나의 기억</p><h2 id={titleId}>7일 후 회상 평가</h2>
    <p className="muted">사진과 선택지 없이 기억나는 이야기를 들려주세요.</p>
    <ErrorBox error={error} retry={()=>setRevision(value=>value+1)} />
    {items ? items.length ? items.map(item=><RecallLink key={item.id} item={item}/>)
      : <p>{albumId ? '이 앨범에는 아직 예정된 평가가 없어요. 새 퀴즈를 시작한 뒤 7일 지연회상 평가를 선택할 수 있어요.'
        : '아직 예정된 평가가 없어요. 새 퀴즈의 첫 문항을 풀기 전에 평가 참여를 선택할 수 있어요.'}</p>
      : !error && <p role="status">평가 일정을 불러오는 중…</p>}
  </section>;
}

export function RecallEnrollment({ sessionId, albumId, photos = [], completed, canEnroll, onContinue }) {
  const [item,setItem]=useState(undefined), [error,setError]=useState(null);
  const [participating,setParticipating]=useState(false), [facts,setFacts]=useState({});
  const [confirmed,setConfirmed]=useState(false), [busy,setBusy]=useState(false), [revision,setRevision]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();
    setItem(undefined); setError(null);
    api.delayedForSession(sessionId,controller.signal).then(value=>{if(!controller.signal.aborted)setItem(value);})
      .catch(error=>{if(!controller.signal.aborted)setError(error);});
    return ()=>controller.abort();
  },[sessionId,completed,revision]);

  async function enroll(event) {
    event.preventDefault();
    if(busy) return;
    setBusy(true); setError(null);
    try {
      const photoFacts=photos.map(photo=>({photoId:photo.id,content:(facts[photo.id]||'').trim()}));
      if(!photoFacts.length || photoFacts.length>100 || photoFacts.some(fact=>!fact.content))
        throw new Error('업로드한 각 사진 아래에 정보를 한 줄씩 입력해주세요. 최대 100장까지 등록할 수 있어요.');
      if(new Set(photoFacts.map(fact=>fact.content)).size!==photoFacts.length)
        throw new Error('각 사진에 서로 다른 정보를 입력해주세요.');
      setItem(await api.enrollDelayed(sessionId,{facts:photoFacts,confirmed}));
      setFacts({}); setParticipating(false);
    } catch(error) { setError(error); }
    finally { setBusy(false); }
  }

  return <section className="delayed-enrollment" aria-label="7일 후 회상 평가 예약">
    <h2>7일 후 회상 평가</h2>
    <ErrorBox error={error} retry={()=>setRevision(value=>value+1)}/>
    {item ? <RecallLink item={item}/> : item === null && !completed && canEnroll ? <>
      {!participating ? <button type="button" onClick={()=>setParticipating(true)}>7일 지연회상 평가 선택</button>
        : <form onSubmit={enroll}>
          <p>앨범에 업로드한 사진을 보며, 각 사진 바로 아래에 실제 경험에 대한 정보를 한 줄씩 적어주세요. 저장한 정보는 7일 뒤 평가의 기준이 되며 변경할 수 없어요.</p>
          {!photos.length ? <p role="alert">먼저 앨범에 사진을 업로드해주세요.</p>
            : photos.length>100 ? <p role="alert">한 번의 평가에는 최대 100장까지 등록할 수 있어요.</p>
            : <div className="delayed-photo-facts">
              {photos.map((photo,index)=>{
                const inputId=`recall-fact-${sessionId}-${photo.id}`;
                return <div className="delayed-photo-fact" key={photo.id}>
                  <PhotoImage albumId={albumId} photo={photo} variant="detail"/>
                  <label htmlFor={inputId}>사진 {index+1} 정보</label>
                  <input id={inputId} type="text" required maxLength={500} disabled={busy}
                    placeholder="예: 부산 해변에서 친구와 산책했다."
                    value={facts[photo.id]||''}
                    onChange={event=>setFacts(previous=>({...previous,[photo.id]:event.target.value}))}/>
                </div>;
              })}
            </div>}
          <p className="hint">사진마다 서로 다른 정보 한 줄, 500자 이내로 입력해주세요. AI가 추측한 내용은 적지 마세요.</p>
          <label className="delayed-check"><input type="checkbox" checked={confirmed}
            onChange={event=>setConfirmed(event.target.checked)} required disabled={busy}/>
            사진 소유자로서 위 정보가 실제 경험과 일치함을 확인했어요.
          </label>
          <div className="delayed-enrollment-actions">
            <button className="button" disabled={busy||!confirmed||!photos.length||photos.length>100||photos.some(photo=>!facts[photo.id]?.trim())}>
              {busy?'등록 중…':'사진 정보 저장하고 평가 예약'}
            </button>
            <button type="button" disabled={busy} onClick={()=>{setParticipating(false);setError(null);}}>평가 참여 취소</button>
          </div>
        </form>}
    </> : item === null ? <p className="muted">이 훈련에는 평가가 등록되지 않았어요. 새 훈련의 첫 문항을 풀기 전에 참여를 선택해주세요.</p>
      : !error && <p role="status">평가 예약을 확인하는 중…</p>}
    {onContinue && (item !== undefined || error) && (!participating || item) &&
      <button className="button" type="button" disabled={busy} onClick={onContinue}>{item ? '훈련 시작' : '평가 없이 훈련 시작'}</button>}
  </section>;
}
