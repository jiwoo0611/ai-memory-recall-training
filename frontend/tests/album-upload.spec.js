import { test, expect } from '@playwright/test';

test('create an album first, upload into it and keep same-region trips separate', async ({ page }) => {
  const token=`test.${Buffer.from(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600})).toString('base64url')}.test`;
  await page.addInitScript(({token})=>sessionStorage.setItem('memory-recall-session',JSON.stringify({accessToken:token,expiresAt:Date.now()+3600000})),{token});
  const albums=[{id:1,title:'부산 봄 여행',photoCount:1,createdAt:'2026-05-01'}];
  const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==','base64');
  const photos={1:[{id:11,originalFilename:'spring.png',contentType:'image/png',sizeBytes:100,metadataLocation:'부산'}],2:[]};
  const uploads=[],filters=[],unexpected=[];
  await page.route('**/api/**',async route=>{
    const req=route.request(),url=new URL(req.url()),path=url.pathname;
    if(path==='/api/users/me')return route.fulfill({json:{id:1,name:'테스트'}});
    if(path==='/api/albums' && req.method()==='POST'){
      const next={id:2,title:req.postDataJSON().title,photoCount:0,createdAt:'2026-11-24'};albums.push(next);return route.fulfill({json:next});
    }
    if(path==='/api/albums')return route.fulfill({json:albums});
    if(path==='/api/users/me/training-sessions')return route.fulfill({json:{content:[]}});
    if(path==='/api/users/me/delayed-recalls'){filters.push(url.searchParams.get('albumId'));return route.fulfill({json:[]});}
    if(path.endsWith('/image'))return route.fulfill({contentType:'image/png',body:image});
    const match=path.match(/^\/api\/albums\/(\d+)(\/photos)?$/);
    if(match){const id=Number(match[1]);
      if(match[2] && req.method()==='POST'){
        uploads.push(id);expect(req.headers()['content-type']).toContain('multipart/form-data');
        photos[id].push({id:21,originalFilename:'autumn.png',contentType:'image/png',sizeBytes:100,metadataLocation:'부산'});
        return route.fulfill({json:[21]});
      }
      return route.fulfill({json:match[2]?photos[id]:albums.find(a=>a.id===id)});
    }
    unexpected.push(path);return route.fulfill({status:404,json:{message:'예상하지 않은 요청'}});
  });
  await page.goto('/#/albums');
  await expect(page.getByRole('button',{name:'+ 사진 추가',exact:true})).toHaveCount(0);
  await page.getByLabel('새 앨범 이름').fill('부산 가을 여행');
  await page.getByRole('button',{name:'+ 새 앨범',exact:true}).click();
  await expect(page).toHaveURL(/#\/albums\/2$/);
  await expect(page.getByRole('heading',{name:'부산 가을 여행',exact:true})).toBeVisible();
  await page.locator('input[type=file]').setInputFiles({name:'autumn.png',mimeType:'image/png',buffer:image});
  await expect(page.getByRole('link',{name:'autumn.png 상세 보기'})).toBeVisible();
  await expect(page.getByRole('link',{name:'spring.png 상세 보기'})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'새 퀴즈 생성·시작'})).toBeVisible();
  await expect(page.getByRole('heading',{name:'7일 후 회상 평가'})).toBeVisible();
  await expect(page.locator('textarea')).toHaveCount(0);
  await page.getByRole('link',{name:'← 앨범 목록'}).click();
  await page.getByRole('link',{name:'부산 봄 여행 앨범 열기'}).click();
  await expect(page.getByRole('link',{name:'spring.png 상세 보기'})).toBeVisible();
  await expect(page.getByRole('link',{name:'autumn.png 상세 보기'})).toHaveCount(0);
  expect(uploads).toEqual([2]);expect(filters).toContain('1');expect(filters).toContain('2');expect(unexpected).toEqual([]);
});
