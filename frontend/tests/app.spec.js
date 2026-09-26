import { test, expect } from "@playwright/test";
const backend = "http://127.0.0.1:18080";
async function login(
  page,
  email = "demo@example.com",
  password = "DemoPass123!",
) {
  await page.goto("/#/login");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "나의 기억 보관함" }),
  ).toBeVisible();
}
test("가입 → 로그인 → 빈 마이페이지 → 로그아웃 → 같은 토큰 401", async ({
  page,
  request,
}) => {
  const email = `browser-${Date.now()}@example.com`;
  await page.goto("/#/signup");
  await page.getByLabel("이름", { exact: true }).fill("새 사용자");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill("ValidPass123!");
  await page.getByRole("button", { name: "회원가입", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("가입이 완료");
  await login(page, email, "ValidPass123!");
  await expect(page.getByText("새 사용자님, 반갑습니다.")).toBeVisible();
  await expect(page.getByText("아직 등록된 사진이 없습니다.")).toBeVisible();
  await expect(
    page.getByText("아직 기록이 없습니다.", { exact: true }),
  ).toBeVisible();
  const token = await page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem("memory-recall-session")).accessToken,
  );
  await page.reload();
  await expect(page.getByText("새 사용자님, 반갑습니다.")).toBeVisible();
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/#\/login$/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("memory-recall-session")),
  ).toBeNull();
  const response = await request.get(`${backend}/api/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(401);
});
test("실제 사진·훈련 페이지네이션, 질문·답변·결과 및 미완료 상세", async ({
  page,
}) => {
  await login(page);
  await expect(page.getByText("시연 사용자님, 반갑습니다.")).toBeVisible();
  await page
    .getByRole("button", { name: "훈련 8 상세 보기", exact: true })
    .click();
  await expect(page.getByText("누구와 함께한 산책인가요?")).toBeVisible();
  await expect(page.getByText("가족과 함께 공원을 걸었습니다.")).toBeVisible();
  await expect(
    page.getByText("가족과의 즐거운 시간을 회상했습니다."),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/mypage-desktop.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "훈련 7 상세 보기", exact: true })
    .click();
  await expect(page.getByText("아직 질문과 답변이 없습니다.")).toBeVisible();
  await expect(page.getByText("아직 훈련 결과가 없습니다.")).toBeVisible();
  const photos = page
    .locator("section.card")
    .filter({ has: page.getByRole("heading", { name: "나의 사진" }) });
  await photos.getByRole("button", { name: "다음", exact: true }).click();
  await expect(photos.getByText("2 / 2")).toBeVisible();
  const sessions = page
    .locator("section.card")
    .filter({ has: page.getByRole("heading", { name: "회상 훈련 기록" }) });
  await sessions.getByRole("button", { name: "다음", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "훈련 1 상세 보기", exact: true }),
  ).toBeVisible();
});
test("이메일 중복과 로그인 실패 안내", async ({ page }) => {
  await page.goto("/#/signup");
  await page.getByLabel("이름", { exact: true }).fill("회원");
  await page.getByLabel("이메일", { exact: true }).fill("demo@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("ValidPass123!");
  await page.getByRole("button", { name: "회원가입", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("이미 가입된 이메일");
  await page.goto("/#/login");
  await page.getByLabel("이메일", { exact: true }).fill("demo@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("Incorrect123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("이메일 또는 비밀번호");
});
test("비로그인 접근 보호 및 서버에서 폐기된 토큰 401 처리", async ({
  page,
  request,
}) => {
  await page.goto("/#/mypage");
  await expect(page).toHaveURL(/#\/login$/);
  await login(page);
  const token = await page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem("memory-recall-session")).accessToken,
  );
  expect(
    (
      await request.post(`${backend}/api/auth/logout`, {
        headers: { Authorization: `Bearer ${token}` },
      })
    ).status(),
  ).toBe(204);
  await page.reload();
  await expect(page).toHaveURL(/#\/login$/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("memory-recall-session")),
  ).toBeNull();
});
test("만료시간 도달 시 로그인 화면 전환", async ({ page }) => {
  await login(page);
  await page.evaluate(() => {
    const value = JSON.parse(sessionStorage.getItem("memory-recall-session"));
    value.expiresAt = Date.now() + 1500;
    sessionStorage.setItem("memory-recall-session", JSON.stringify(value));
  });
  await page.reload();
  await expect(page).toHaveURL(/#\/login$/, { timeout: 6000 });
  expect(
    await page.evaluate(() => sessionStorage.getItem("memory-recall-session")),
  ).toBeNull();
});
test("로그아웃 네트워크 실패 시 성공으로 표시하지 않고 재시도 가능", async ({
  page,
}) => {
  await login(page);
  await page.route("**/api/auth/logout", (route) => route.abort());
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "로그아웃이 완료되지 않았습니다",
  );
  expect(
    await page.evaluate(
      () => !!sessionStorage.getItem("memory-recall-session"),
    ),
  ).toBe(true);
  await page.unroute("**/api/auth/logout");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/#\/login$/);
});
test("모바일 화면·홈 및 상세 404 표시", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /사진에 담긴 순간/ }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/home-mobile.png",
    fullPage: true,
  });
  await login(page);
  await page.route("**/api/users/me/training-sessions/8", (route) =>
    route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: "훈련 기록을 찾을 수 없습니다." }),
    }),
  );
  await page
    .getByRole("button", { name: "훈련 8 상세 보기", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "훈련 기록을 찾을 수 없습니다.",
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/mypage-mobile.png",
    fullPage: true,
  });
});

test("회상 훈련 입력 → 임시 제출 → 결과 집계", async ({ page }) => {
  await page.goto("/#/training-demo");
  await expect(
    page.getByRole("heading", { name: "사진 속 이야기를 천천히 떠올려보세요." }),
  ).toBeVisible();

  await page
    .getByLabel("나의 답변")
    .fill("부산 해운대에서 가족과 함께 촬영했습니다.");
  await page.getByRole("button", { name: "다음 질문" }).click();

  await page.getByRole("button", { name: "기억나지 않음" }).click();
  await page.getByRole("button", { name: "다음 질문" }).click();

  await page
    .getByLabel("나의 답변")
    .fill("바다를 보며 함께 걸었던 순간이 기억납니다.");
  await page.getByRole("button", { name: "답변 제출하기" }).click();

  await expect(page).toHaveURL(/#\/training-result-demo$/);
  await expect(
    page.getByRole("heading", { name: "오늘의 기억을 기록했어요." }),
  ).toBeVisible();
  await expect(page.getByText("부산 해운대에서 가족과 함께 촬영했습니다.")).toBeVisible();
  await expect(page.getByText("기억나지 않음", { exact: true })).toBeVisible();
});

test("회상 훈련은 빈 답변 제출을 막고 모바일에서도 넘치지 않음", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/#/training-demo");
  await page.getByRole("button", { name: "다음 질문" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "답변을 입력하거나 다른 응답 방식을 선택해주세요.",
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
