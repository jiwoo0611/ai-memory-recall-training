import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { date, ErrorBox } from '../components/UI';
import { recallLabels } from '../components/DelayedRecallSection';

export default function DelayedRecallPage({ id }) {
  const [data,setData]=useState(null),[text,setText]=useState(''),[cannot,setCannot]=useState(false),[error,setError]=useState(null),[busy,setBusy]=useState(false),[notice,setNotice]=useState(''),[confirm,setConfirm]=useState(false),[revision,setRevision]=useState(0),[dirty,setDirty]=useState(false);
  const lock=useRef(false);
  function accept(value){setData(value);setText(value.responseText||'');setCannot(value.cannotRecall);setDirty(false);}
  useEffect(()=>{const c=new AbortController();api.delayedDetail(id,c.signal).then(x=>{if(!c.signal.aborted)accept(x);}).catch(e=>{if(!c.signal.aborted)setError(e);});return ()=>c.abort();},[id,revision]);
  useEffect(()=>{if(!dirty)return;const warn=e=>{e.preventDefault();e.returnValue='';};window.addEventListener('beforeunload',warn);return ()=>window.removeEventListener('beforeunload',warn);},[dirty]);
  async function act(kind){if(lock.current)return;lock.current=true;setBusy(true);setError(null);setNotice('');
    try{const body={version:data.version,text,cannotRecall:cannot};const result=kind==='start'?await api.startDelayed(id):kind==='save'?await api.saveDelayed(id,body):await api.submitDelayed(id,body);accept(result);setConfirm(false);setNotice(kind==='save'?'임시 저장했어요.':kind==='submit'?'응답이 제출되었어요.':'평가를 시작했어요.');}
    catch(e){setError(e);setConfirm(false);}finally{setBusy(false);lock.current=false;}}
  if(!data)return <section className="card"><h1>7일 후 회상 평가</h1><ErrorBox error={error} retry={()=>setRevision(v=>v+1)}/>{!error&&<p role="status">평가를 불러오는 중…</p>}</section>;
  const writing=data.status==='IN_PROGRESS',submitted=['SUBMITTED','GRADED'].includes(data.status);
  return <section className="card delayed-page">
    <a className="back-link" href="#/mypage" onClick={e=>{if(dirty){e.preventDefault();setNotice('이동 전 임시 저장을 눌러 작성 내용을 보관해 주세요.');}}}>← 마이페이지</a>
    <p className="eyebrow">{recallLabels[data.status]}</p><h1>7일 후 회상 평가</h1>
    <p className="muted">훈련 #{data.sourceSessionId} · 훈련 완료 {date(data.completedAt)}</p>
    {['WAITING_TRAINING','NOT_DUE'].includes(data.status)&&<><p>훈련 완료부터 7일이 지나면 참여할 수 있어요.</p><p>평가 가능 {date(data.availableAt)}</p><button onClick={()=>setRevision(v=>v+1)}>상태 새로고침</button></>}
    {data.status==='AVAILABLE'&&<><h2>기억나는 만큼 편하게 적어 주세요</h2><p>표시된 훈련에서 다룬 경험을 사진과 선택지 없이 떠올려 주세요. 기억이 나지 않는 부분은 적지 않아도 괜찮아요.</p><p>평가 중에는 사진이나 이전 훈련 기록을 확인하지 말아 주세요. 작성 중 잠시 쉬려면 임시 저장을 눌러 주세요.</p><button className="button" disabled={busy} onClick={()=>act('start')}>평가 시작</button></>}
    {writing&&<form onSubmit={e=>{e.preventDefault();setConfirm(true);}}>
      <label htmlFor="delayed-response"><h2>이전에 훈련한 경험에 대해 기억나는 내용을 적어 주세요.</h2></label>
      <textarea id="delayed-response" className="delayed-response" rows={10} maxLength={10000} value={text} disabled={busy||cannot} onChange={e=>{setText(e.target.value);setDirty(true);setConfirm(false);}}/>
      <p className="hint">{text.length} / 10000자 · {dirty?'저장하지 않은 변경이 있어요.':'저장된 내용이에요.'}</p>
      <label className="delayed-check"><input type="checkbox" checked={cannot} disabled={busy||!!text.trim()} onChange={e=>{setCannot(e.target.checked);setDirty(true);setConfirm(false);}}/>기억나는 내용이 없어요</label>
      <p className="hint">입력 내용이 있으면 지운 뒤 기억나지 않음을 선택할 수 있어요.</p>
      <div className="delayed-actions"><button type="button" disabled={busy} onClick={()=>act('save')}>임시 저장</button><button className="button" disabled={busy||(!cannot&&!text.trim())}>최종 제출</button></div>
      {confirm&&<div role="group" aria-label="제출 확인"><p>제출하면 답변을 수정할 수 없어요. 제출할까요?</p><button type="button" className="button" disabled={busy} onClick={()=>act('submit')}>제출 확정</button><button type="button" disabled={busy} onClick={()=>setConfirm(false)}>계속 작성</button></div>}
    </form>}
    {submitted&&<><h2>{data.status==='GRADED'?'회상 평가 결과':'회상 평가가 제출되었어요'}</h2><p>제출 {date(data.submittedAt)}</p>
      {data.status==='SUBMITTED'?<><p>작성한 내용을 기준 사실과 대조한 뒤 결과를 안내해 드릴게요.</p><button onClick={()=>setRevision(v=>v+1)}>결과 새로고침</button></>:<>
        <p className="delayed-rate">{data.recallRate}%</p><p>정확히 회상한 사실 {data.recalledCount}개 / 평가 대상 사실 {data.factCount}개</p>
        <p>실제 평가 간격 {Math.floor(data.elapsedSeconds/86400)}일 {Math.floor(data.elapsedSeconds%86400/3600)}시간</p>
        <p className="hint">회상률은 확인된 사실을 얼마나 떠올렸는지를 나타내며, 최초 객관식 정답률과는 다른 지표예요.</p>
        <details><summary>사실별 평가 보기</summary>{data.facts.map(f=><article className="question" key={f.id}><h3>{f.content}</h3><p>{f.recalled?'정확히 회상함':'회상 확인 안 됨'}</p>{f.evidenceQuote&&<blockquote>{f.evidenceQuote}</blockquote>}{f.note&&<p>{f.note}</p>}</article>)}</details>
      </>}
      <details><summary>내가 제출한 답변</summary><p className="recall-text">{data.cannotRecall?'기억나는 내용이 없어요':data.responseText}</p></details>
    </>}
    <p role="status">{busy?'처리 중…':notice}</p><ErrorBox error={error}/>
    {error?.status===409&&writing&&<p>다른 창의 변경 여부를 확인해 주세요. 현재 입력을 별도로 복사한 뒤 <button onClick={()=>setRevision(v=>v+1)}>최신 기록 불러오기</button>를 선택할 수 있어요.</p>}
  </section>;
}
