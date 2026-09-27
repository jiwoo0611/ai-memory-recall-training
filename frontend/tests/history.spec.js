import { test, expect } from "@playwright/test";

const storageKey = "memory-recall-session";
const album = { id: 1, title: "여행", createdAt: "2026-01-01" };
const photo = {
  id: 2, originalFilename: "여행.jpg", contentType: "image/png",
  sizeBytes: 100, confirmedLocation: "서울", confirmedTakenOn: "2026-01-01",
  aiClues: '{"summary":"함께한 여행"}',
};
test.beforeEach(async ({ page }) => {
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
    if (path.endsWith("/image")) {
      return route.fulfill({ contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==", "base64") });
    }
    const responses = {
      "/api/users/me": { id: 1, name: "테스트", email: "test@example.com" },
      "/api/users/me/photos": { content: [photo] },
      "/api/users/me/training-sessions": { content: [], totalElements: 0, totalPages: 0 },
      "/api/albums": [album],
      "/api/albums/1": album,
      "/api/albums/1/photos": [photo],
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
  await page.locator("nav").getByRole("link", { name: "마이페이지" }).click();
  await page.getByRole("heading", { name: "여행", exact: true }).click();
  await expect(page.getByRole("img", { name: "여행.jpg", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expect(page.getByRole("heading", { name: "여행 회상 질문" })).toBeVisible();
  await expect(page.getByText("함께한 여행", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "AI 다시 분석" }).click();
  await expect(page.getByText("새 분석 결과", { exact: true })).toBeVisible();
  await home(page);
  for (const target of ["/albums/1/photos/2", "/mypage", "/"]) {
    await page.goBack();
    await expect.poll(() => page.evaluate(() => location.hash)).toBe(`#${target}`);
  }
  for (const target of ["/mypage", "/albums/1/photos/2", "/"]) {
    await page.goForward();
    await expect.poll(() => page.evaluate(() => location.hash)).toBe(`#${target}`);
  }
});

for (const path of ["/mypage", "/albums/1/photos/2"]) {
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
  });
}

test("logout clears session and protects both routes including history traversal", async ({ page }) => {
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
  await page.goto("/#/albums/1/photos/2");
  await expect(page).toHaveURL(/#\/login$/);
});

test("authenticated login/signup visits replace without adding redirect entries", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  for (const path of ["/login", "/signup"]) {
    await home(page);
    const before = await length(page);
    await page.evaluate((path) => { location.hash = path; }, path);
    await expect(page).toHaveURL(/#\/mypage$/);
    expect(await length(page)).toBe(before + 1);
    await page.goBack();
    await expect(page).toHaveURL(/#\/$/);
    await page.goForward();
    await expect(page).toHaveURL(/#\/mypage$/);
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
