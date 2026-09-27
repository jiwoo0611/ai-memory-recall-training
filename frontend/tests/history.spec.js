import { test, expect } from "@playwright/test";

const storageKey = "memory-recall-session";
const album = { id: 1, title: "여행", createdAt: "2026-01-01" };
const photo = {
  id: 2, originalFilename: "여행.jpg", contentType: "image/png",
  sizeBytes: 100, confirmedLocation: "서울", confirmedTakenOn: "2026-01-01",
  aiClues: '{"summary":"함께한 여행"}',
};
test.beforeEach(async ({ page }) => {
  let albums = [album];
  let photos = [photo];
  const token = `fixture.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString("base64url")}.fixture`;
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    if (path === "/api/auth/login") {
      return route.fulfill({ json: { accessToken: token, tokenType: "Bearer", expiresIn: 3600 } });
    }
    if (path === "/api/auth/signup") return route.fulfill({ status: 201, json: {} });
    expect(route.request().headers().authorization).toBe(`Bearer ${token}`);
    if (path === "/api/auth/logout") return route.fulfill({ status: 204 });
    if (path === "/api/albums" && route.request().method() === "POST") {
      const created = { ...album, id: 3, title: route.request().postDataJSON().title };
      albums = [...albums, created];
      return route.fulfill({ status: 201, json: created });
    }
    if (path === "/api/albums/3" && route.request().method() === "DELETE") {
      albums = albums.filter((item) => item.id !== 3);
      return route.fulfill({ status: 204 });
    }
    if (path === "/api/albums/1/photos" && route.request().method() === "POST") {
      expect(route.request().headers()["content-type"]).toContain("multipart/form-data");
      const uploaded = { ...photo, id: 4, originalFilename: "추가.png" };
      photos = [...photos, uploaded];
      return route.fulfill({ status: 201, json: [uploaded] });
    }
    if (path === "/api/albums/1/photos/4" && route.request().method() === "DELETE") {
      photos = photos.filter((item) => item.id !== 4);
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith("/image")) {
      return route.fulfill({ contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==", "base64") });
    }
    const responses = {
      "/api/users/me": { id: 1, name: "테스트", email: "test@example.com" },
      "/api/users/me/photos": { content: [photo] },
      "/api/users/me/training-sessions": { content: [], totalElements: 0, totalPages: 0 },
      "/api/albums": albums,
      "/api/albums/1": album,
      "/api/albums/1/photos": photos,
      "/api/albums/1/photos/2": photo,
      "/api/albums/1/photos/2/analyze": { ...photo, aiClues: '{"summary":"새 분석 결과"}' },
    };
    expect(responses[path], `Unexpected API: ${path}`).toBeDefined();
    await route.fulfill({ json: responses[path] });
  });
});

async function login(page) {
  await page.getByLabel("이메일", { exact: true }).fill("test@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("ValidPass123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/#\/mypage$/);
  await expect(page.getByRole("heading", { name: "나의 기억 보관함" })).toBeVisible();
}
const length = (page) => page.evaluate(() => history.length);
async function home(page) {
  await page.locator("nav").getByRole("link", { name: "홈", exact: true }).click();
  await expect(page).toHaveURL(/#\/$/);
}

test("login replaces entry; home → mypage → back returns home; reload retains session", async ({ page }) => {
  await page.goto("/#/");
  await page.locator("nav").getByRole("link", { name: "로그인", exact: true }).click();
  const before = await length(page);
  await login(page);
  expect(await length(page)).toBe(before);
  await page.goBack();
  await expect(page).toHaveURL(/#\/$/);
  await page.goForward();
  await expect(page).toHaveURL(/#\/mypage$/);
  await home(page);
  await page.locator("nav").getByRole("link", { name: "마이페이지" }).click();
  await page.goBack();
  await expect(page).toHaveURL(/#\/$/);
  const session = await page.evaluate((key) => sessionStorage.getItem(key), storageKey);
  await page.reload();
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBe(session);
  await expect(page.getByRole("button", { name: "로그아웃", exact: true })).toBeVisible();
});

test("album/photo detail and ordinary back/forward preserve visit order", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await home(page);
  const before = await length(page);
  await page.locator("nav").getByRole("link", { name: "마이페이지" }).click();
  await expect(page).toHaveURL(/#\/mypage$/);
  expect(await length(page)).toBe(before + 1);
  await page.getByRole("heading", { name: "여행", exact: true }).click();
  await expect(page).toHaveURL(/#\/albums\/1$/);
  expect(await length(page)).toBe(before + 2);
  await expect(page.getByRole("img", { name: "여행.jpg", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expect(page.getByRole("heading", { name: "여행 회상 질문" })).toBeVisible();
  expect(await length(page)).toBe(before + 3);
  await expect(page.getByText("함께한 여행", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "AI 다시 분석" }).click();
  await expect(page.getByText("새 분석 결과", { exact: true })).toBeVisible();
  for (const target of ["/albums/1", "/mypage", "/"]) {
    await page.goBack();
    await expectRouteScreen(page, target);
    expect(await length(page)).toBe(before + 3);
  }
  for (const target of ["/mypage", "/albums/1", "/albums/1/photos/2"]) {
    await page.goForward();
    await expectRouteScreen(page, target);
    expect(await length(page)).toBe(before + 3);
  }
});

async function expectRouteScreen(page, path) {
  await expect.poll(() => page.evaluate(() => location.hash)).toBe(`#${path}`);
  if (path === "/albums/1") {
    await expect(page.getByRole("heading", { name: "여행", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "여행.jpg 상세 보기" })).toBeVisible();
    await expect(page.getByPlaceholder("새 앨범 이름")).toHaveCount(0);
  } else if (path === "/mypage") {
    await expect(page.getByPlaceholder("새 앨범 이름")).toBeVisible();
    await expect(page.getByRole("link", { name: "여행.jpg 상세 보기" })).toHaveCount(0);
  } else if (path === "/albums/1/photos/2") {
    await expect(page.getByRole("heading", { name: "여행 회상 질문" })).toBeVisible();
  } else {
    await expect(page.getByRole("heading", { name: /사진에 담긴 순간/ })).toBeVisible();
  }
}

test("album back returns mypage in one step and forward restores album", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await home(page);
  await page.locator("nav").getByRole("link", { name: "마이페이지" }).click();
  await page.getByRole("heading", { name: "여행", exact: true }).click();
  await expectRouteScreen(page, "/albums/1");
  await page.goBack();
  await expectRouteScreen(page, "/mypage");
  await page.goBack();
  await expectRouteScreen(page, "/");
  await page.goForward();
  await expectRouteScreen(page, "/mypage");
  await page.goForward();
  await expectRouteScreen(page, "/albums/1");
});

for (const path of ["/mypage", "/albums/1", "/albums/1/photos/2"]) {
  test(`unauthenticated direct access replaces protected route: ${path}`, async ({ page }) => {
    await page.goto("/#/");
    const before = await length(page);
    await page.evaluate((path) => { location.hash = path; }, path);
    await expect(page).toHaveURL(/#\/login$/);
    expect(await length(page)).toBe(before + 1);
    await page.goBack();
    await expect(page).toHaveURL(/#\/$/);
    await page.goForward();
    await expect(page).toHaveURL(/#\/login$/);
    await page.goto(`/#${path}`);
    await expect(page).toHaveURL(/#\/login$/);
  });
}

test("logout clears session and protects all routes including history traversal", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await home(page);
  await page.locator("nav").getByRole("link", { name: "마이페이지" }).click();
  const before = await length(page);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/#\/login$/);
  expect(await length(page)).toBe(before);
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBeNull();
  await page.goBack();
  await expect(page).toHaveURL(/#\/$/);
  await page.goBack();
  await expect(page).toHaveURL(/#\/login$/);
  for (const path of ["/mypage", "/albums/1", "/albums/1/photos/2"]) {
    await page.goto(`/#${path}`);
    await expect(page).toHaveURL(/#\/login$/);
  }
});

test("authenticated public page visits are not forcibly redirected", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  for (const path of ["/login", "/signup"]) {
    await home(page);
    const before = await length(page);
    await page.evaluate((path) => { location.hash = path; }, path);
    await expect.poll(() => page.evaluate(() => location.hash)).toBe(`#${path}`);
    await expect(page.getByRole("button", { name: path === "/login" ? "로그인" : "회원가입", exact: true })).toBeVisible();
    expect(await length(page)).toBe(before + 1);
    await page.goBack();
    await expect(page).toHaveURL(/#\/$/);
    await page.goForward();
    await expect.poll(() => page.evaluate(() => location.hash)).toBe(`#${path}`);
  }
});

test("401 expiry replaces current entry and clears session", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  const before = await length(page);
  await page.route("**/api/users/me", (route) => route.fulfill({ status: 401, json: { message: "expired" } }));
  await page.reload();
  await expect(page).toHaveURL(/#\/login$/);
  expect(await length(page)).toBe(before);
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBeNull();
});

test("album and photo routes survive reload with the same session and history", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await page.getByRole("link", { name: "여행 앨범 열기" }).click();
  await expectRouteScreen(page, "/albums/1");
  const session = await page.evaluate((key) => sessionStorage.getItem(key), storageKey);
  const before = await length(page);
  await page.reload();
  await expectRouteScreen(page, "/albums/1");
  expect(await length(page)).toBe(before);
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expectRouteScreen(page, "/albums/1/photos/2");
  await page.reload();
  await expectRouteScreen(page, "/albums/1/photos/2");
  expect(await length(page)).toBe(before + 1);
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBe(session);
  await page.goBack();
  await expectRouteScreen(page, "/albums/1");
});

test("explicit parent links add one entry and back returns the actual previous screen", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await page.getByRole("link", { name: "여행 앨범 열기" }).click();
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expectRouteScreen(page, "/albums/1/photos/2");
  const before = await length(page);
  await page.getByRole("link", { name: "← 앨범 사진 목록" }).click();
  await expectRouteScreen(page, "/albums/1");
  expect(await length(page)).toBe(before + 1);
  await page.getByRole("link", { name: "← 앨범 목록" }).click();
  await expectRouteScreen(page, "/mypage");
  expect(await length(page)).toBe(before + 2);
  await page.goBack();
  await expectRouteScreen(page, "/albums/1");
  await page.goBack();
  await expectRouteScreen(page, "/albums/1/photos/2");
});

test("album creation/deletion and photo upload/deletion do not add navigation entries", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  const before = await length(page);
  await page.getByPlaceholder("새 앨범 이름").fill("새 여행");
  await page.getByRole("button", { name: "+ 새 앨범", exact: true }).click();
  await expect(page.getByRole("link", { name: "새 여행 앨범 열기" })).toHaveAttribute("href", "#/albums/3");
  expect(await length(page)).toBe(before);
  page.on("dialog", (dialog) => dialog.accept());
  await page.locator("article").filter({ has: page.getByRole("heading", { name: "새 여행", exact: true }) }).getByRole("button", { name: "앨범 삭제" }).click();
  await expect(page.getByRole("link", { name: "새 여행 앨범 열기" })).toHaveCount(0);
  await expectRouteScreen(page, "/mypage");
  expect(await length(page)).toBe(before);
  await page.getByRole("link", { name: "여행 앨범 열기" }).click();
  await expectRouteScreen(page, "/albums/1");
  await page.locator('input[type="file"]').setInputFiles({
    name: "추가.png", mimeType: "image/png",
    buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==", "base64"),
  });
  await expect(page.getByRole("link", { name: "추가.png 상세 보기" })).toBeVisible();
  await page.locator("article").filter({ has: page.getByRole("link", { name: "추가.png 상세 보기" }) }).getByRole("button", { name: "삭제", exact: true }).click();
  await expect(page.getByRole("link", { name: "추가.png 상세 보기" })).toHaveCount(0);
  expect(await length(page)).toBe(before + 1);
  await page.goBack();
  await expectRouteScreen(page, "/mypage");
});

test("missing album stays on its route and back returns home", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await home(page);
  await page.route("**/api/albums/99{,/**}", (route) => route.fulfill({ status: 404, json: { message: "앨범을 찾을 수 없습니다." } }));
  await page.evaluate(() => { location.hash = "/albums/99"; });
  await expect(page.getByRole("alert")).toContainText("앨범을 찾을 수 없습니다.");
  await expect(page).toHaveURL(/#\/albums\/99$/);
  await page.goBack();
  await expectRouteScreen(page, "/");
});

test("JWT timer expiry on an album clears session without adding a history entry", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await page.getByRole("link", { name: "여행 앨범 열기" }).click();
  await expectRouteScreen(page, "/albums/1");
  await page.evaluate((key) => {
    const session = JSON.parse(sessionStorage.getItem(key));
    session.expiresAt = Date.now() + 1500;
    sessionStorage.setItem(key, JSON.stringify(session));
  }, storageKey);
  const before = await length(page);
  await page.reload();
  await expect(page).toHaveURL(/#\/login$/);
  expect(await length(page)).toBe(before);
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBeNull();
});
