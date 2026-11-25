import { test, expect } from "@playwright/test";

const storageKey = "memory-recall-session";
const album = { photoCount: 1, id: 1, title: "여행", createdAt: "2026-01-01" };
const photo = {
  id: 2, originalFilename: "여행.jpg", contentType: "image/png",
  sizeBytes: 100, confirmedLocation: "서울", confirmedTakenOn: "2026-01-01",
  aiClues: '{"questionClues":["함께한 여행"]}',
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
    if (path === "/api/albums/1" && route.request().method() === "PATCH") {
      const updated = { ...album, title: route.request().postDataJSON().title };
      albums = albums.map(item => item.id === 1 ? updated : item);
      return route.fulfill({ json: updated });
    }
    if (path === "/api/albums/1/photos" && route.request().method() === "POST") {
      expect(route.request().headers()["content-type"]).toContain("multipart/form-data");
      const uploaded = { ...photo, id: 4, originalFilename: "추가.png" };
      photos = [...photos, uploaded];
      return route.fulfill({ status: 201, json: [uploaded.id] });
    }
    if (path === "/api/albums/1/photos/4" && route.request().method() === "DELETE") {
      photos = photos.filter((item) => item.id !== 4);
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith("/image")) {
      return route.fulfill({ contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==", "base64") });
    }
    const responses = {
      "/api/users/me/delayed-recalls": [],
      "/api/users/me/training-sessions/7/delayed-recall": null,
      "/api/users/me": { id: 1, name: "테스트", email: "test@example.com" },
      "/api/users/me/photos": { content: [photo] },
      "/api/users/me/training-sessions": { content: [], totalElements: 0, totalPages: 0 },
      "/api/albums": albums,
      "/api/albums/1": album,
      "/api/albums/3": albums.find(item => item.id === 3),
      "/api/albums/3/photos": [],
      "/api/albums/1/photos": photos,
      "/api/albums/1/photos/2": photo,
      "/api/albums/1/photos/2/analyze": { ...photo, aiClues: '{"questionClues":["새 분석 결과"]}' },
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함" }).click();
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함" }).click();
  await expect(page).toHaveURL(/#\/albums$/);
  expect(await length(page)).toBe(before + 1);
  await page.getByRole("heading", { name: "여행", exact: true }).click();
  await expect(page).toHaveURL(/#\/albums\/1$/);
  expect(await length(page)).toBe(before + 2);
  await expect(page.getByRole("img", { name: "여행.jpg", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expect(page.getByRole("heading", { name: "여행.jpg 분석 정보" })).toBeVisible();
  expect(await length(page)).toBe(before + 3);
  await expect(page.getByText("함께한 여행", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "AI 다시 분석" }).click();
  await expect(page.getByText("새 분석 결과", { exact: true })).toBeVisible();
  for (const target of ["/albums/1", "/albums", "/"]) {
    await page.goBack();
    await expectRouteScreen(page, target);
    expect(await length(page)).toBe(before + 3);
  }
  for (const target of ["/albums", "/albums/1", "/albums/1/photos/2"]) {
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
  } else if (path === "/albums") {
    await expect(page.getByPlaceholder("새 앨범 이름")).toBeVisible();
    await expect(page.getByRole("link", { name: "여행.jpg 상세 보기" })).toHaveCount(0);
  } else if (path === "/albums/1/photos/2") {
    await expect(page.getByRole("heading", { name: "여행.jpg 분석 정보" })).toBeVisible();
  } else {
    await expect(page.getByRole("heading", { name: /사진에 담긴 순간/ })).toBeVisible();
  }
}

test("album back returns library in one step and forward restores album", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await home(page);
  await page.locator("nav").getByRole("link", { name: "사진 보관함" }).click();
  await page.getByRole("heading", { name: "여행", exact: true }).click();
  await expectRouteScreen(page, "/albums/1");
  await page.goBack();
  await expectRouteScreen(page, "/albums");
  await page.goBack();
  await expectRouteScreen(page, "/");
  await page.goForward();
  await expectRouteScreen(page, "/albums");
  await page.goForward();
  await expectRouteScreen(page, "/albums/1");
});

for (const path of ["/mypage", "/training/7", "/albums/1/quiz/7", "/albums", "/albums/1", "/albums/1/photos/2"]) {
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함" }).click();
  const before = await length(page);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/#\/login$/);
  expect(await length(page)).toBe(before);
  expect(await page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBeNull();
  await page.goBack();
  await expect(page).toHaveURL(/#\/$/);
  await page.goBack();
  await expect(page).toHaveURL(/#\/login$/);
  for (const path of ["/mypage", "/training/7", "/albums/1/quiz/7", "/albums", "/albums/1", "/albums/1/photos/2"]) {
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함", exact: true }).click();
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함", exact: true }).click();
  await page.getByRole("link", { name: "여행 앨범 열기" }).click();
  await page.getByRole("link", { name: "여행.jpg 상세 보기" }).click();
  await expectRouteScreen(page, "/albums/1/photos/2");
  const before = await length(page);
  await page.getByRole("link", { name: "← 앨범 사진 목록" }).click();
  await expectRouteScreen(page, "/albums/1");
  expect(await length(page)).toBe(before + 1);
  await page.getByRole("link", { name: "← 앨범 목록" }).click();
  await expectRouteScreen(page, "/albums");
  expect(await length(page)).toBe(before + 2);
  await page.goBack();
  await expectRouteScreen(page, "/albums/1");
  await page.goBack();
  await expectRouteScreen(page, "/albums/1/photos/2");
});

test("album creation opens its detail; upload/deletion do not add navigation entries", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await page.locator("nav").getByRole("link", { name: "사진 보관함", exact: true }).click();
  const before = await length(page);
  await page.getByPlaceholder("새 앨범 이름").fill("새 여행");
  await page.getByRole("button", { name: "+ 새 앨범", exact: true }).click();
  await expect(page).toHaveURL(/#\/albums\/3$/);
  await expect(page.getByRole("heading", { name: "새 여행", exact: true })).toBeVisible();
  expect(await length(page)).toBe(before + 1);
  await page.goBack();
  await expect(page.getByRole("link", { name: "새 여행 앨범 열기" })).toHaveAttribute("href", "#/albums/3");
  page.on("dialog", (dialog) => dialog.accept());
  await page.locator("article").filter({ has: page.getByRole("heading", { name: "새 여행", exact: true }) }).getByRole("button", { name: "앨범 삭제" }).click();
  await expect(page.getByRole("link", { name: "새 여행 앨범 열기" })).toHaveCount(0);
  await expectRouteScreen(page, "/albums");
  expect(await length(page)).toBe(before + 1);
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
  await expectRouteScreen(page, "/albums");
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
  await page.locator("nav").getByRole("link", { name: "사진 보관함", exact: true }).click();
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

for (const width of [1280, 375]) {
  test(`home CTA and separate library/profile pages at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/#/");
    await expect(page.getByRole("link", { name: "로그인하고 시작하기" })).toHaveAttribute("href", "#/login");
    await expect(page.locator("nav").getByRole("link", { name: "사진 보관함" })).toHaveCount(0);
    await page.getByRole("link", { name: "로그인하고 시작하기" }).click();
    await login(page);
    await expect(page.getByRole("heading", { name: "나의 성장 기록" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "최근 활동" })).toBeVisible();
    await expect(page.getByRole("heading", { name: /회상 훈련 기록/ })).toBeVisible();
    await expect(page.getByPlaceholder("새 앨범 이름")).toHaveCount(0);
    await home(page);
    await page.getByRole("link", { name: "사진 보관함으로 이동" }).click();
    await expectRouteScreen(page, "/albums");
    await expect(page.getByRole("heading", { name: "사진 보관함", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "나의 성장 기록" })).toHaveCount(0);
    await expect(page.getByText("1장", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "+ 사진 추가", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.locator("nav").getByRole("link", { name: "마이페이지", exact: true }).click();
    await expect(page).toHaveURL(/#\/mypage$/);
    await page.goBack();
    await expectRouteScreen(page, "/albums");
    await page.goForward();
    await expect(page.getByRole("heading", { name: "나의 성장 기록" })).toBeVisible();
  });
}

test("existing training and album quiz routes remain accessible", async ({ page }) => {
  const session = {
    id: 7, status: "IN_PROGRESS", startedAt: "2026-01-01",
    photo: { ...photo, albumId: 1 }, result: null,
    questions: [{ id: 8, questionOrder: 1, content: "사진 속 장소는 어디인가요?", type: "PHOTO_CLUE", format: "FREE_TEXT", options: [] }],
  };
  await page.route("**/api/users/me/training-sessions/7", route => route.fulfill({ json: session }));
  await page.goto("/#/login");
  await login(page);
  await page.goto("/#/training/7");
  await expect(page.getByRole("heading", { name: "사진 속 기억을 들려주세요" })).toBeVisible();
  session.questions[0] = { ...session.questions[0], format: "MULTIPLE_CHOICE", options: ["서울", "부산", "제주", "인천"] };
  await page.goto("/#/albums/1/quiz/7");
  await page.getByRole("button", { name: "평가 없이 훈련 시작", exact: true }).click();
  await expect(page.getByRole("heading", { name: "앨범 회상 퀴즈" })).toBeVisible();
  await expect(page.getByRole("radio")).toHaveCount(4);
});

test("library album rename keeps the existing API and history", async ({ page }) => {
  await page.goto("/#/login");
  await login(page);
  await page.locator("nav").getByRole("link", { name: "사진 보관함" }).click();
  const before = await length(page);
  await page.getByRole("button", { name: "이름 변경", exact: true }).click();
  await page.getByPlaceholder("앨범 이름", { exact: true }).fill("새 추억");
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByRole("link", { name: "새 추억 앨범 열기" })).toBeVisible();
  expect(await length(page)).toBe(before);
});

test("mypage retains saved growth, activity and training detail data", async ({ page }) => {
  const saved = {
    id: 7, displayName: "여행 1", status: "COMPLETED", startedAt: "2026-01-01", completedAt: "2026-01-01",
    photo: { ...photo, albumId: 1 },
    result: { correctCount: 8, totalCount: 10, incorrectCount: 2, answeredCount: 10,
      cannotRecallCount: 0, mismatchCount: 0, feedbackStatus: "COMPLETED", summary: "저장된 훈련 요약" },
    questions: [],
  };
  await page.route("**/api/users/me/training-sessions?*", route => route.fulfill({ json: {
    content: [saved], totalElements: 1, totalPages: 1, number: 0, first: true, last: true,
  } }));
  await page.route("**/api/users/me/training-sessions/7", route => route.fulfill({ json: saved }));
  await page.goto("/#/login");
  await login(page);
  await expect(page.getByText("80 XP", { exact: true })).toBeVisible();
  await expect(page.getByText("80%", { exact: true }).first()).toBeVisible();
  await expect(page.getByLabel("최근 훈련별 정답률")).toBeVisible();
  await expect(page.locator(".activity")).toContainText("여행.jpg");
  await page.getByRole("button", { name: "여행 1 상세 보기" }).click();
  await expect(page.getByLabel("훈련 상세")).toContainText("저장된 훈련 요약");
  await expect(page.getByRole("link", { name: "저장된 결과 보기" })).toHaveAttribute("href", "#/training/7");
});
