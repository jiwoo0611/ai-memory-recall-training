import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { date, ErrorBox } from '../components/UI';

export default function RecallReviewPage({ id }) {
  const [data,setData]=useState(null),[marks,setMarks]=useState({}),[error,setError]=useState(null),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0);
  const lock=useRef(false);
  useEffect(()=>{const c=new AbortController();setData(null);setError(null);setMarks({});
    (id?api.delayedReview(id,c.signal):api.delayedQueue(c.signal)).then(x=>{if(!c.signal.aborted)setData(x);}).catch(e=>{if(!c.signal.aborted)setError(e);});return ()=>c.abort();},[id,revision]);
  async function submit(e){e.preventDefault();if(lock.current)return;lock.current=true;setBusy(true);setError(null);
    try {const body={marks:data.facts.map(f=>({factId:f.id,recalled:marks[f.id]?.recalled==='yes',evidenceQuote:marks[f.id]?.quote||'',note:marks[f.id]?.note||''}))};setData(await api.gradeDelayed(id,body));}
    catch(e){setError(e);}finally{lock.current=false;setBusy(false);}}
  return <section className="card delayed-page"><a href="#/mypage">← 마이페이지</a><h1>회상 평가 채점</h1>
    <ErrorBox error={error} retry={()=>setRevision(v=>v+1)}/>
    {!data&&!error&&<p role="status">불러오는 중…</p>}
    {data&&!id&&(data.length?data.map(item=><article className="delayed-item" key={item.id}><p>평가 #{item.id} · 훈련 #{item.sourceSessionId}<br/>제출 {date(item.submittedAt)}</p><a className="button" href={`#/recall-review/${item.id}`}>채점하기</a></article>):<p>채점을 기다리는 응답이 없어요.</p>)}
    {data&&id&&<><p>평가 #{data.id} · 프로토콜 {data.protocolVersion}</p><h2>제출된 자유 서술</h2><p className="recall-text">{data.cannotRecall?'기억나는 내용 없음':data.responseText}</p>
      {data.status==='GRADED'?<><p role="status">채점이 확정되었어요. 회상률 {data.recallRate}%</p><a href="#/recall-review">채점 목록으로</a></>:<form onSubmit={submit}>
        <p>기준 사실과 표현의 의미가 일치하는지 판정해 주세요. 반복한 사실은 한 번만 인정하고, 추측·모순은 인정하지 않습니다. 인정한 사실마다 실제 응답의 근거 문장을 그대로 입력해 주세요.</p>
        {data.facts.map(f=>{const m=marks[f.id]||{};const change=v=>setMarks(old=>({...old,[f.id]:{...old[f.id],...v}}));return <fieldset className="recall-question" key={f.id} disabled={busy}><legend>{f.order}. {f.content}</legend>
          <label htmlFor={`mark-${f.id}`}>회상 판정</label><select id={`mark-${f.id}`} required value={m.recalled||''} onChange={e=>change({recalled:e.target.value})}><option value="">선택해주세요</option><option value="yes" disabled={data.cannotRecall}>정확히 회상함</option><option value="no">회상 확인 안 됨</option></select>
          {m.recalled==='yes'&&<><label htmlFor={`quote-${f.id}`}>응답에서 인용한 근거 문장</label><textarea id={`quote-${f.id}`} required maxLength={2000} value={m.quote||''} onChange={e=>change({quote:e.target.value})}/></>}
          <label htmlFor={`note-${f.id}`}>판정 이유</label><textarea id={`note-${f.id}`} maxLength={1000} value={m.note||''} onChange={e=>change({note:e.target.value})}/>
        </fieldset>;})}<p>확정 후에는 이 화면에서 변경할 수 없습니다.</p><button className="button" disabled={busy||data.facts.some(f=>!marks[f.id]?.recalled)}>{busy?'저장 중…':'채점 확정'}</button>
      </form>}
    </>}
  </section>;
}
