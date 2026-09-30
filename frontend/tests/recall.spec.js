import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
const backend = "http://127.0.0.1:18080";

async function prepare(page, request) {
  const login = await request.post(`${backend}/api/auth/login`, { data: { email: "demo@example.com", password: "DemoPass123!" } });
  expect(login.ok()).toBeTruthy();
  const auth = await login.json();
  const headers = { Authorization: `Bearer ${auth.accessToken}` };
  const sessions = await (await request.get(`${backend}/api/users/me/training-sessions?size=100`, { headers })).json();
  const source = sessions.content.at(-1).id;
  const created = await request.post(`${backend}/api/users/me/training-sessions/${source}/retrain`, { headers, data: { requestId: randomUUID() } });
  expect(created.ok()).toBeTruthy();
  const { sessionId } = await created.json();
  await page.goto("/#/login");
  await page.getByLabel("이메일", { exact: true }).fill("demo@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("DemoPass123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/mypage/);
  await page.goto(`/#/training/${sessionId}`);
  await expect(page.getByRole("heading", { name: "사진 속 기억을 들려주세요" })).toBeVisible();
  return { headers, sessionId };
}

test("empty validation, failed submission, retry, summary failure, reload and relogin preserve answers", async ({ page, request }) => {
  const { sessionId } = await prepare(page, request);
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("alert")).toContainText("모든 질문");
  const questions = page.locator("fieldset.recall-question");
  await questions.nth(0).getByLabel("직접 답변", { exact: true }).check();
  await questions.nth(0).getByLabel("떠오르는 기억").fill("가족과 함께 공원을 걸었습니다.");
  await questions.nth(1).getByLabel("기억나지 않아요").check();
  await questions.nth(2).getByLabel("질문이 사진과 맞지 않아요").check();
  await page.route(`**/training-sessions/${sessionId}/answers`, (route) => route.abort(), { times: 1 });
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByText("입력한 내용은 유지되어 있습니다.", { exact: false })).toBeVisible();
  await expect(questions.nth(0).getByLabel("떠오르는 기억")).toHaveValue("가족과 함께 공원을 걸었습니다.");
  await page.route(`**/training-sessions/${sessionId}/feedback`, (route) => route.abort(), { times: 1 });
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("heading", { name: "훈련 결과", exact: true })).toBeVisible();
  await expect(page.getByText("응답 3 / 3문항")).toBeVisible();
  await expect(page.getByText("가족과 함께 공원을 걸었습니다.", { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText("가족과 함께 공원을 걸었습니다.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "요약 생성·재시도" }).click();
  await expect(page.getByLabel("회상 요약")).toContainText("가족과 함께 공원을 걸었습니다.");
  await page.screenshot({ path: "test-results/recall-result-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "test-results/recall-result-mobile.png", fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await page.getByLabel("이메일", { exact: true }).fill("demo@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("DemoPass123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/mypage/);
  await page.goto(`/#/training/${sessionId}`);
  await expect(page.getByText("가족과 함께 공원을 걸었습니다.", { exact: true })).toBeVisible();
});

test("lost successful response can replay without duplicates and new training keeps old result", async ({ page, request }) => {
  const { sessionId, headers } = await prepare(page, request);
  for (const field of await page.locator("fieldset.recall-question").all()) await field.getByLabel("기억나지 않아요").check();
  await page.screenshot({ path: "test-results/recall-form.png", fullPage: true });
  await page.route(`**/training-sessions/${sessionId}/answers`, async (route) => {
    const response = await route.fetch(); expect(response.ok()).toBeTruthy(); await route.abort();
  }, { times: 1 });
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("heading", { name: "훈련 결과", exact: true })).toBeVisible();
  await expect(page.getByLabel("회상 요약")).toContainText("서술한 답변이 없습니다");
  await page.getByRole("button", { name: "같은 사진으로 새 훈련" }).click();
  await expect(page.getByRole("heading", { name: "사진 속 기억을 들려주세요" })).toBeVisible();
  expect(page.url()).not.toContain(`/training/${sessionId}`);
  const saved = await (await request.get(`${backend}/api/users/me/training-sessions/${sessionId}`, { headers })).json();
  expect(saved.questions.filter((q) => q.answer).length).toBe(3);
  expect(saved.result.cannotRecallCount).toBe(3);
});

test("detail and history errors offer retry without losing the training form", async ({ page, request }) => {
  const { sessionId, headers } = await prepare(page, request);
  // Create enough records for pagination even when running against a fresh local DB.
  for (let index = 0; index < 6; index++) {
    const created = await request.post(`${backend}/api/users/me/training-sessions/${sessionId}/retrain`, { headers, data: { requestId: randomUUID() } });
    expect(created.ok()).toBeTruthy();
  }
  await page.route(`**/training-sessions/${sessionId}`, (route) => route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ message: "일시적인 조회 오류" }) }), { times: 1 });
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("일시적인 조회 오류");
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.getByRole("heading", { name: "사진 속 기억을 들려주세요" })).toBeVisible();
  await page.route("**/training-sessions?photoId=**", (route) => route.abort(), { times: 1 });
  await page.reload();
  await expect(page.locator("aside").getByRole("alert")).toBeVisible();
  await page.locator("aside").getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.locator("aside .recall-history-item").first()).toBeVisible();
  await expect(page.locator("aside").getByRole("navigation", { name: "목록 페이지" })).toBeVisible();
  await page.locator("aside").getByRole("button", { name: "다음", exact: true }).click();
  await expect(page.locator("aside")).toContainText("2 /");
});
