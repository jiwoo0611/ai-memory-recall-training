import { test, expect } from '@playwright/test';

const initial = { id: 7, displayName: '부산 1', sourceSessionId: 12, status: 'AVAILABLE', condition: 'QUIZ', protocolVersion: 'FREE_RECALL_7D_V1', completedAt: '2026-11-17T09:00:00Z', availableAt: '2026-11-24T09:00:00Z', responseText: null, cannotRecall: false, version: 0, factCount: 2, facts: [] };
async function fixture(page, change = {}) {
  let value = { ...initial, ...change };
  const unexpected=[];
  const token=`test.${Buffer.from(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600})).toString('base64url')}.test`;
  await page.addInitScript(({token})=>sessionStorage.setItem('memory-recall-session',JSON.stringify({accessToken:token,expiresAt:Date.now()+3600000})),{token});
  await page.route('**/api/**',async route=>{
    const request=route.request(), path=new URL(request.url()).pathname;
    expect(request.headers().authorization).toBe(`Bearer ${token}`);
    if(path==='/api/users/me')return route.fulfill({json:{id:1,name:'테스트',email:'fixture@example.com'}});
    if(path==='/api/users/me/photos'||path==='/api/users/me/training-sessions')return route.fulfill({json:{content:[],totalElements:0,totalPages:0}});
    if(path==='/api/users/me/delayed-recalls')return route.fulfill({json:[value]});
    if(path==='/api/users/me/delayed-recalls/7')return route.fulfill({json:value});
    if(path.endsWith('/7/start')) {value={...value,status:'IN_PROGRESS',startedAt:new Date().toISOString()};return route.fulfill({json:value});}
    if(path.endsWith('/7/draft')||path.endsWith('/7/submit')){
      const body=request.postDataJSON();expect(body.version).toBe(value.version);
      value={...value,responseText:body.text,cannotRecall:body.cannotRecall,version:value.version+1};
      if(path.endsWith('/submit')) value={...value,status:'SUBMITTED',submittedAt:new Date().toISOString()};
      return route.fulfill({json:value});
    }
    unexpected.push(path);return route.fulfill({status:404,json:{message:'예상하지 않은 요청'}});
  });
  return {unexpected};
}

test('list opens cue-free evaluation and restores server draft after reload',async({page})=>{
  const f=await fixture(page);
  await page.goto('/#/mypage');await expect(page.getByRole('heading',{name:'부산 1의 회상 평가'})).toBeVisible();await page.getByRole('link',{name:'회상 평가 시작'}).click();
  await expect(page.getByRole('heading',{name:'기억나는 만큼 편하게 적어 주세요'})).toBeVisible();
  await page.getByRole('button',{name:'평가 시작',exact:true}).click();
  expect(await page.locator('img').count()).toBe(0);
  await page.getByLabel('이전에 훈련한 경험에 대해 기억나는 내용을 적어 주세요.').fill('친구와 바닷가에서 산책했어요.');
  await page.getByRole('button',{name:'임시 저장',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('임시 저장했어요.');
  await page.reload();await expect(page.locator('#delayed-response')).toHaveValue('친구와 바닷가에서 산책했어요.');
  await page.getByRole('button',{name:'최종 제출',exact:true}).click();
  await page.getByRole('button',{name:'계속 작성'}).click();
  await expect(page.locator('#delayed-response')).toBeEditable();
  await page.getByRole('button',{name:'최종 제출',exact:true}).click();
  await page.getByRole('button',{name:'제출 확정'}).click();
  await expect(page.getByRole('heading',{name:'회상 평가가 제출되었어요'})).toBeVisible();
  await expect(page.locator('textarea')).toHaveCount(0);
  await page.reload();await expect(page.getByRole('heading',{name:'회상 평가가 제출되었어요'})).toBeVisible();
  expect(f.unexpected).toEqual([]);
});
test('not-due state has no input or start control',async({page})=>{
  await fixture(page,{status:'NOT_DUE',availableAt:new Date(Date.now()+86400000).toISOString()});await page.goto('/#/delayed-recall/7');
  await expect(page.getByText('훈련 완료부터 7일이 지나면 참여할 수 있어요.')).toBeVisible();
  await expect(page.locator('textarea')).toHaveCount(0);await expect(page.getByRole('button',{name:'평가 시작',exact:true})).toHaveCount(0);
});
test('cannot recall is a valid response and graded result separates recall from quiz score',async({page})=>{
  await fixture(page);await page.goto('/#/delayed-recall/7');await page.getByRole('button',{name:'평가 시작',exact:true}).click();
  await page.getByLabel('기억나는 내용이 없어요').check();await expect(page.locator('#delayed-response')).toBeDisabled();
  await page.getByRole('button',{name:'최종 제출',exact:true}).click();await page.getByRole('button',{name:'제출 확정'}).click();
  await expect(page.getByRole('heading',{name:'회상 평가가 제출되었어요'})).toBeVisible();
});
test('mobile result remains readable',async({page})=>{
  await page.setViewportSize({width:390,height:844});
  await fixture(page,{status:'GRADED',recalledCount:1,recallRate:50,elapsedSeconds:604800,submittedAt:'2026-11-24T09:00:00Z',gradedAt:'2026-11-24T10:00:00Z',responseText:'부산에 갔다',facts:[{id:1,content:'부산에 갔다',recalled:true,evidenceQuote:'부산에 갔다',note:'의미 일치'}]});
  await page.goto('/#/delayed-recall/7');await expect(page.getByText('50%',{exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  await page.screenshot({path:'test-results/delayed-mobile.png',fullPage:true});
});


async function enrollmentFixture(page, failFirstSave = false) {
  const token = `test.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now()/1000)+3600 })).toString('base64url')}.test`;
  await page.addInitScript(({token})=>sessionStorage.setItem('memory-recall-session',JSON.stringify({accessToken:token,expiresAt:Date.now()+3600000})),{token});
  const photos = [
    {id:21,albumId:3,originalFilename:'beach.png',contentType:'image/png',sizeBytes:100},
    {id:22,albumId:3,originalFilename:'cake.png',contentType:'image/png',sizeBytes:100},
  ];
  const session={id:12,displayName:'부산 1',photo:photos[0],status:'IN_PROGRESS',questions:[{id:101,format:'MULTIPLE_CHOICE',questionOrder:1,content:'어디에서 산책했나요?',options:['해변','공원','산','집'],displayPhotoIds:[]}],result:null};
  let saved=null;
  const submissions=[],unexpected=[];
  await page.route('**/api/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname;
    if(path==='/api/users/me')return route.fulfill({json:{id:1,name:'테스트'}});
    if(path==='/api/users/me/training-sessions/12')return route.fulfill({json:session});
    if(path==='/api/users/me/photos/21/training-sessions')return route.fulfill({json:{content:[],totalPages:0}});
    if(path==='/api/albums/3')return route.fulfill({json:{id:3,title:'부산 여행'}});
    if(path==='/api/albums/3/photos')return route.fulfill({json:photos});
    if(path.endsWith('/image'))return route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==','base64')});
    if(path==='/api/users/me/training-sessions/12/delayed-recall'){
      if(req.method()==='POST'){
        submissions.push(req.postDataJSON());
        if(failFirstSave && submissions.length===1)return route.fulfill({status:503,json:{message:'일시적인 저장 오류'}});
        saved={...initial,status:'WAITING_TRAINING',completedAt:null,availableAt:null};
      }
      return route.fulfill({json:saved});
    }
    unexpected.push(path);return route.fulfill({status:404,json:{message:'예상하지 않은 요청'}});
  });
  return {submissions,unexpected};
}
test('ordinary quiz skips all photo information inputs and creates no evaluation',async({page})=>{
  const f=await enrollmentFixture(page);
  await page.goto('/#/albums/3/quiz/12');
  await expect(page.getByRole('button',{name:'7일 지연회상 평가 선택',exact:true})).toBeVisible();
  await expect(page.getByLabel('사진 1 정보')).toHaveCount(0);
  await expect(page.locator('main img')).toHaveCount(0);
  await page.getByRole('button',{name:'평가 없이 훈련 시작',exact:true}).click();
  await expect(page.getByRole('heading',{name:'앨범 회상 퀴즈'})).toBeVisible();
  expect(f.submissions).toEqual([]);expect(f.unexpected).toEqual([]);
});
test('delayed evaluation shows each uploaded photo above its input and persists photo IDs',async({page})=>{
  await page.setViewportSize({width:390,height:844});
  const f=await enrollmentFixture(page,true);
  await page.goto('/#/albums/3/quiz/12');
  await page.getByRole('button',{name:'7일 지연회상 평가 선택',exact:true}).click();
  await expect(page.getByRole('img',{name:'beach.png',exact:true})).toBeVisible();
  await expect(page.getByRole('img',{name:'cake.png',exact:true})).toBeVisible();
  await expect(page.getByRole('textbox')).toHaveCount(2);
  await expect(page.locator('textarea')).toHaveCount(0);
  const first=page.locator('.delayed-photo-fact').first();
  expect(await first.evaluate(el=>(el.querySelector('img').compareDocumentPosition(el.querySelector('input')) & Node.DOCUMENT_POSITION_FOLLOWING)!==0)).toBeTruthy();
  await page.getByLabel('사진 1 정보').fill('부산 해변에서 산책했다.');
  await page.getByLabel('사진 2 정보').fill('친구와 케이크를 먹었다.');
  await page.getByLabel('사진 소유자로서 위 정보가 실제 경험과 일치함을 확인했어요.').check();
  await page.getByRole('button',{name:'사진 정보 저장하고 평가 예약',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('일시적인 저장 오류');
  await expect(page.getByLabel('사진 1 정보')).toHaveValue('부산 해변에서 산책했다.');
  await page.getByRole('button',{name:'사진 정보 저장하고 평가 예약',exact:true}).click();
  await expect(page.getByRole('button',{name:'훈련 시작',exact:true})).toBeVisible();
  expect(f.submissions[1]).toEqual({facts:[{photoId:21,content:'부산 해변에서 산책했다.'},{photoId:22,content:'친구와 케이크를 먹었다.'}],confirmed:true});
  await page.reload();
  await expect(page.getByRole('button',{name:'훈련 시작',exact:true})).toBeVisible();
  await expect(page.getByLabel('사진 1 정보')).toHaveCount(0);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  expect(f.unexpected).toEqual([]);
});
test('retired manual grading page is not available',async({page})=>{
  await fixture(page);
  await page.goto('/#/recall-review');
  await expect(page.getByRole('heading',{name:'페이지를 찾을 수 없습니다.'})).toBeVisible();
  await expect(page.getByRole('link',{name:'제출된 회상 평가 채점하기'})).toHaveCount(0);
});
