
import { trainingName } from "../api/trainingName";
import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { date, ErrorBox } from '../components/UI';
import { recallLabels } from '../components/DelayedRecallSection';

export default function DelayedRecallPage({ id }) {
  const [data, setData] = useState(null);
  const [text, setText] = useState('');
  const [cannot, setCannot] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [grading, setGrading] = useState(false);
  const [notice, setNotice] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [revision, setRevision] = useState(0);
  const [dirty, setDirty] = useState(false);

  const lock = useRef(false);

  function accept(value) {
    setData(value);
    setText(value.responseText || '');
    setCannot(value.cannotRecall);
    setDirty(false);
  }

  useEffect(() => {
    const c = new AbortController();

    setError(null);

    api.delayedDetail(id, c.signal)
      .then(x => {
        if (!c.signal.aborted) accept(x);
      })
      .catch(e => {
        if (!c.signal.aborted) setError(e);
      });

    return () => c.abort();
  }, [id, revision]);

  useEffect(() => {
    if (!dirty && !busy) return;

    const warn = e => {
      e.preventDefault();
      e.returnValue = '';
    };

    window.addEventListener('beforeunload', warn);

    return () =>
      window.removeEventListener('beforeunload', warn);
  }, [dirty, busy]);

  async function act(kind) {
    if (lock.current) return;

    lock.current = true;
    setBusy(true);
    setError(null);
    setNotice('');

    const isGrading = kind === 'submit' || kind === 'grade';
    setGrading(isGrading);

    try {
      const body = {
        version: data.version,
        text,
        cannotRecall: cannot
      };

      let result;

      if (kind === 'start') {
        result = await api.startDelayed(id);
      } else if (kind === 'save') {
        result = await api.saveDelayed(id, body);
      } else if (kind === 'submit') {
        result = await api.submitDelayed(id, body);
      } else if (kind === 'grade') {
        result = await api.gradeDelayed(id);
      }

      accept(result);
      setConfirm(false);

      if (kind === 'save') {
        setNotice('임시 저장했어요.');
      } else if (isGrading) {
        setNotice(
          result.status === 'GRADED'
            ? 'AI 채점이 완료되었어요.'
            : '응답이 제출되었어요.'
        );
      } else {
        setNotice('평가를 시작했어요.');
      }

    } catch (e) {
      setError(e);
      setConfirm(false);

      // 제출 또는 채점 요청 실패 시
      // 실제 서버의 저장 상태를 다시 확인
      if (isGrading) {
        try {
          const latest = await api.delayedDetail(id);

          if (latest.status === 'GRADED') {
            accept(latest);
            setError(null);
            setNotice('AI 채점이 완료되었어요.');
          } else if (latest.status === 'SUBMITTED') {
            accept(latest);
            setNotice(
              '답변은 저장되었지만 채점이 완료되지 않았어요. 채점을 다시 시도해 주세요.'
            );
          } else {
            setNotice(
              '제출이 확인되지 않았어요. 입력한 답변을 유지합니다.'
            );
          }
        } catch {
          setNotice(
            '서버 상태를 확인할 수 없어요. 입력한 답변을 유지합니다.'
          );
        }
      }

    } finally {
      setBusy(false);
      setGrading(false);
      lock.current = false;
    }
  }

  if (!data) {
    return (
      <section className="card">
        <h1>7일 후 회상 평가</h1>

        <ErrorBox
          error={error}
          retry={() => setRevision(v => v + 1)}
        />

        {!error && (
          <p role="status">평가를 불러오는 중…</p>
        )}
      </section>
    );
  }

  const writing = data.status === 'IN_PROGRESS';

  const submitted = ['SUBMITTED', 'GRADED']
    .includes(data.status);

  return (
    <section className="card delayed-page">

      <a
        className="back-link"
        href="#/mypage"
        onClick={e => {
          if (busy) {
            e.preventDefault();
            return;
          }

          if (dirty) {
            e.preventDefault();
            setNotice(
              '이동 전 임시 저장을 눌러 작성 내용을 보관해 주세요.'
            );
          }
        }}
      >
        ← 마이페이지
      </a>

      <p className="eyebrow">
        {recallLabels[data.status]}
      </p>

      <h1>7일 후 회상 평가</h1>

      <p className="muted">
        {trainingName(data)} · 훈련 완료 {date(data.completedAt)}
      </p>

      {['WAITING_TRAINING', 'NOT_DUE'].includes(data.status) && (
        <>
          <p>훈련 완료부터 7일이 지나면 참여할 수 있어요.</p>
          <p>평가 가능 {date(data.availableAt)}</p>

          <button
            disabled={busy}
            onClick={() => setRevision(v => v + 1)}
          >
            상태 새로고침
          </button>
        </>
      )}

      {data.status === 'AVAILABLE' && (
        <>
          <h2>기억나는 만큼 편하게 적어 주세요</h2>

          <p>
            표시된 훈련에서 다룬 경험을 사진과 선택지 없이
            떠올려 주세요. 기억이 나지 않는 부분은 적지 않아도
            괜찮아요.
          </p>

          <p>
            평가 중에는 사진이나 이전 훈련 기록을 확인하지
            말아 주세요. 작성 중 잠시 쉬려면 임시 저장을
            눌러 주세요.
          </p>

          <button
            className="button"
            disabled={busy}
            onClick={() => act('start')}
          >
            평가 시작
          </button>
        </>
      )}

      {writing && (
        <form onSubmit={e => {
          e.preventDefault();
          if (!busy) setConfirm(true);
        }}>

          <label htmlFor="delayed-response">
            <h2>
              이전에 훈련한 경험에 대해 기억나는 내용을 적어 주세요.
            </h2>
          </label>

          <textarea
            id="delayed-response"
            className="delayed-response"
            rows={10}
            maxLength={10000}
            value={text}
            disabled={busy || cannot}
            onChange={e => {
              setText(e.target.value);
              setDirty(true);
              setConfirm(false);
            }}
          />

          <p className="hint">
            {text.length} / 10000자 ·
            {dirty
              ? ' 저장하지 않은 변경이 있어요.'
              : ' 저장된 내용이에요.'}
          </p>

          <label className="delayed-check">
            <input
              type="checkbox"
              checked={cannot}
              disabled={busy || !!text.trim()}
              onChange={e => {
                setCannot(e.target.checked);
                setDirty(true);
                setConfirm(false);
              }}
            />
            기억나는 내용이 없어요
          </label>

          <p className="hint">
            입력 내용이 있으면 지운 뒤 기억나지 않음을
            선택할 수 있어요.
          </p>

          <div className="delayed-actions">

            <button
              type="button"
              disabled={busy}
              onClick={() => act('save')}
            >
              임시 저장
            </button>

            <button
              className="button"
              disabled={busy || (!cannot && !text.trim())}
            >
              {grading ? 'AI 채점 중…' : '최종 제출'}
            </button>

          </div>

          {confirm && (
            <div role="group" aria-label="제출 확인">

              <p>제출하면 답변을 수정할 수 없어요. 제출할까요?</p>

              <button
                type="button"
                className="button"
                disabled={busy}
                onClick={() => act('submit')}
              >
                {grading ? 'AI 채점 중…' : '제출 확정'}
              </button>

              <button
                type="button"
                disabled={busy}
                onClick={() => setConfirm(false)}
              >
                계속 작성
              </button>

            </div>
          )}
        </form>
      )}

      {submitted && (
        <>
          <h2>
            {data.status === 'GRADED'
              ? 'AI 회상 평가 결과'
              : '회상 평가가 제출되었어요'}
          </h2>

          <p>제출 {date(data.submittedAt)}</p>

          {data.status === 'SUBMITTED' ? (
            <>
              <p>
                답변은 저장되었지만 아직 AI 채점이 완료되지
                않았어요.
              </p>

              <div className="delayed-actions">

                <button
                  className="button"
                  disabled={busy}
                  onClick={() => act('grade')}
                >
                  {grading ? 'AI 채점 중…' : 'AI 채점 다시 시도'}
                </button>

                <button
                  disabled={busy}
                  onClick={() => setRevision(v => v + 1)}
                >
                  결과 새로고침
                </button>

              </div>
            </>
          ) : (
            <>
              <p className="delayed-rate">
                {data.recallRate}%
              </p>

              <p>전체 평가 대상: {data.factCount}개</p>

              <p>회상 성공: {data.recalledCount}개</p>

              <p>
                회상 실패: {data.factCount - data.recalledCount}개
              </p>

              <p>
                실제 평가 간격{' '}
                {Math.floor(data.elapsedSeconds / 86400)}일{' '}
                {Math.floor(data.elapsedSeconds % 86400 / 3600)}시간
              </p>

              <p className="hint">
                회상률은 확인된 사실을 얼마나 떠올렸는지를
                나타내며, 최초 객관식 정답률과는 다른 지표예요.
              </p>

              <p className="hint">
                AI 채점 결과는 실제 회상 내용을 비교한
                참고 지표이며 의학적 진단 결과가 아니에요.
              </p>

              <details open>
                <summary>사실별 AI 채점 결과</summary>

                {(data.facts || []).map((f, index) => (
                  <article className="question" key={f.id}>

                    <h3>사진 정보 {index + 1}</h3>

                    <p>
                      <strong>최초 입력 정보</strong>
                    </p>

                    <p>{f.content}</p>

                    <p>
                      <strong>채점 결과: </strong>
                      {f.recalled
                        ? '회상 성공 (1점)'
                        : '회상 실패 (0점)'}
                    </p>

                    <p>
                      <strong>사용자 응답 근거</strong>
                    </p>

                    {f.evidenceQuote
                      ? <blockquote>{f.evidenceQuote}</blockquote>
                      : <p>일치하는 응답 없음</p>}

                    <p>
                      <strong>채점 이유</strong>
                    </p>

                    <p>{f.note || '채점 이유 없음'}</p>

                  </article>
                ))}
              </details>
            </>
          )}

          <details>
            <summary>내가 제출한 답변</summary>

            <p className="recall-text">
              {data.cannotRecall
                ? '기억나는 내용이 없어요'
                : data.responseText}
            </p>
          </details>
        </>
      )}

      <p role="status">
        {grading
          ? 'AI가 회상 내용을 분석하고 있어요. 잠시만 기다려 주세요.'
          : busy
            ? '처리 중…'
            : notice}
      </p>

      <ErrorBox error={error}/>

      {error?.status === 409 && writing && (
        <p>
          다른 창의 변경 여부를 확인해 주세요.
          현재 입력을 별도로 복사한 뒤{' '}

          <button
            disabled={busy}
            onClick={() => setRevision(v => v + 1)}
          >
            최신 기록 불러오기
          </button>

          를 선택할 수 있어요.
        </p>
      )}

    </section>
  );
}
