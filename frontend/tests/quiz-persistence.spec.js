import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
const backend = "http://127.0.0.1:18081";
async function login(page) {
  await page.goto("/#/login");
  await page.getByLabel("이메일", { exact: true }).fill("quiz-browser@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("BrowserTest123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/mypage/);
}
async function prepare(page, request) {
  let auth;
  await expect(async () => {
    const response = await request.post(`${backend}/api/auth/login`, { data: { email: "quiz-browser@example.com", password: "BrowserTest123!" } });
    expect(response.ok()).toBeTruthy(); auth = await response.json();
  }).toPass({ timeout: 10000 });
  const headers = { Authorization: `Bearer ${auth.accessToken}` };
  const list = await (await request.get(`${backend}/api/users/me/training-sessions?size=100`, { headers })).json();
  const response = await request.post(`${backend}/api/users/me/training-sessions/${list.content.at(-1).id}/retrain`, { headers, data: { requestId: randomUUID() } });
  expect(response.ok()).toBeTruthy();
  const { sessionId } = await response.json();
  await login(page); await page.goto(`/#/training/${sessionId}`);
  await expect(page.getByRole("heading", { name: "앨범 회상 퀴즈", exact: true })).toBeVisible();
  return { sessionId, headers };
}
async function choose(page) {
  await page.getByRole("radio", { name: "1 보기 A" }).click();
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await page.getByRole("radio", { name: "2 보기 B" }).click();
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await page.getByRole("radio", { name: "1 보기 A" }).click();
}

test("real DB result survives reload and login; retraining preserves old session", async ({ page, request }) => {
  const { sessionId, headers } = await prepare(page, request);
  await choose(page); await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("heading", { name: "회상 퀴즈 결과" })).toBeVisible();
  await expect(page.locator(".quiz-score-summary strong")).toHaveText("2 / 3");
  await expect(page.locator(".wrong-answer-card")).toHaveCount(3);
  const feedback = page.getByRole("region", { name: "훈련 피드백" });
  await expect(feedback.locator(".quiz-feedback-card")).toHaveCount(1);
  await expect(feedback).toContainText("나머지 1문항을 다시 확인해보세요.");
  await expect(feedback.locator("dd")).toHaveText(["보기 B", "보기 A"]);
  const resultUrl = page.url();
  await feedback.getByRole("button", { name: "질문 2 보기" }).click();
  await expect(page.getByLabel("질문 2 상세", { exact: true })).toBeFocused();
  expect(page.url()).toBe(resultUrl);
  await page.reload(); await expect(page.locator(".quiz-score-summary strong")).toHaveText("2 / 3");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await login(page);
  await page.getByRole("button", { name: `훈련 ${sessionId} 상세 보기`, exact: true }).click();
  await expect(page.getByLabel("훈련 상세")).toContainText("정답 2 / 3");
  await page.getByRole("link", { name: "저장된 결과 보기", exact: true }).click();
  await expect(page.locator(".quiz-score-summary strong")).toHaveText("2 / 3");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole("button", { name: "같은 사진으로 새 훈련", exact: true }).click();
  await expect(page.getByRole("heading", { name: "앨범 회상 퀴즈", exact: true })).toBeVisible();
  expect(page.url()).not.toContain(`/training/${sessionId}`);
  const saved = await (await request.get(`${backend}/api/users/me/training-sessions/${sessionId}`, { headers })).json();
  expect(saved.result.correctCount).toBe(2); expect(saved.questions.filter(q => q.answer).length).toBe(3);
});

test("failed submission keeps selections and lost committed response recovers through 409", async ({ page, request }) => {
  const { sessionId } = await prepare(page, request); await choose(page);
  await page.route(`**/training-sessions/${sessionId}/submit`, route => route.abort(), { times: 1 });
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("radio", { name: "1 보기 A" })).toHaveAttribute("aria-checked", "true");
  await page.route(`**/training-sessions/${sessionId}/submit`, async route => {
    const response = await route.fetch(); expect(response.ok()).toBeTruthy(); await route.abort();
  }, { times: 1 });
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "제출하고 결과 보기" }).click();
  await expect(page.locator(".quiz-score-summary strong")).toHaveText("2 / 3");
});

test("feedback failure display keeps stored answers; result fetch offers retry", async ({ page, request }) => {
  const { sessionId, headers } = await prepare(page, request);
  const detail = await (await request.get(`${backend}/api/users/me/training-sessions/${sessionId}`, { headers })).json();
  await request.post(`${backend}/api/users/me/training-sessions/${sessionId}/submit`, { headers,
    data: { answers: detail.questions.map(q => ({ questionId: q.id, selectedOptionIndex: 0 })) } });
  // Backend generator failures are covered by DB integration tests; exercise the UI state here.
  await page.route(`**/training-sessions/${sessionId}`, async route => {
    const response = await route.fetch(); const data = await response.json();
    data.result.feedbackStatus = "FAILED"; data.result.summary = null;
    await route.fulfill({ response, json: data });
  });
  await page.reload(); await expect(page.getByText("생성 실패", { exact: true })).toBeVisible();
  await expect(page.locator(".quiz-score-summary strong")).toHaveText("3 / 3");
  await expect(page.locator(".quiz-feedback-card")).toHaveCount(0);
  await expect(page.getByText("다시 확인할 오답은 없습니다.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "피드백 생성·재시도" }).click();
  await expect(page.getByText("생성 완료", { exact: true })).toBeVisible();
  await page.unroute(`**/training-sessions/${sessionId}`);
  await page.route(`**/training-sessions/${sessionId}`, route => route.abort(), { times: 1 });
  await page.reload(); await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.locator(".quiz-score-summary strong")).toHaveText("3 / 3");
});
