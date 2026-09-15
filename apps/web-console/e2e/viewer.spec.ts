import { expect, Page, test } from "@playwright/test";

const origin = "http://127.0.0.1:3100";
const createdAt = "2026-09-15T00:00:00Z";
const fixtures = [
  { id: "release-a", version: "v-e2e-a", serviceName: "checkout-e2e", hash: "aaaaaaaaaaaa1111", eventType: "RELEASE_APPROVED", sequence: 101 },
  { id: "release-b", version: "v-e2e-b", serviceName: "billing-e2e", hash: "bbbbbbbbbbbb2222", eventType: "RELEASE_ABORTED", sequence: 102 },
];

async function isolateApi(page: Page, deniedId?: string) {
  let authenticated = false;
  const unexpected: string[] = [];
  const session = { user: { id: "viewer-e2e", displayName: "E2E Viewer", roles: ["VIEWER"], demo: true }, csrfToken: "test-only-csrf", expiresAt: "2099-01-01T00:00:00Z" };
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== origin) {
      unexpected.push(`External request: ${url.origin}`);
      return route.abort();
    }
    if (!url.pathname.startsWith("/control-api/")) return route.continue();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    const path = url.pathname.slice("/control-api".length);
    if (request.method() === "POST" && path === "/session/demo") {
      if (request.headers()["x-csrf-token"] !== "test-only-csrf") {
        unexpected.push("Missing demo CSRF header");
        return json({ code: "CSRF_INVALID" }, 403);
      }
      authenticated = true;
      return json(session);
    }
    if (request.method() !== "GET") {
      unexpected.push(`Unexpected mutation: ${request.method()} ${path}`);
      return json({ code: "FORBIDDEN" }, 403);
    }
    if (path === "/session/providers") return json({ oidc: false, loginUrl: null });
    if (path === "/session/csrf") return json({ headerName: "X-CSRF-TOKEN", token: "test-only-csrf" });
    if (path === "/session") return json(authenticated ? session : { code: "UNAUTHENTICATED" }, authenticated ? 200 : 401);
    if (!authenticated) return json({ code: "UNAUTHENTICATED" }, 401);
    if (path === "/releases") return json({ items: fixtures.map((item) => ({ id: item.id, version: item.version, status: "ANALYZING", createdAt, context: { serviceName: item.serviceName, environmentName: "isolated-e2e" } })) });
    if (path === "/audit-events") {
      const item = fixtures.find((fixture) => fixture.id === url.searchParams.get("aggregateId"));
      if (item) return json({ items: [{ id: `audit-${item.id}`, eventType: item.eventType, actorType: "SYSTEM", actorId: null, occurredAt: createdAt, correlationId: `correlation-${item.id}`, chainSequence: item.sequence }] });
    }
    const item = fixtures.find((fixture) => path.startsWith(`/releases/${fixture.id}`));
    if (item) {
      if (item.id === deniedId) return json({ code: "FORBIDDEN" }, 403);
      if (path.endsWith("/analyses")) return json([{ id: `analysis-${item.id}`, stepIndex: 0, status: "COMPLETED", attempts: 1, verdict: "PASS", reasonCode: "ALL_RULES_PASSED", evidence: [{ metric_key: "HTTP_5XX_RATE", verdict: "PASS", reason_code: "ALL_RULES_PASSED", baseline_value: 0.001, canary_value: 0.002, threshold: 0.01, query_template_id: "e2e-query", canary_query_hash: item.hash }] }]);
      if (path.endsWith("/events")) return route.fulfill({ status: 200, contentType: "text/event-stream", body: `event: release-state\ndata: ${JSON.stringify({ releaseStatus: "ANALYZING", steps: [{ index: 0, weight: 10, status: "EVALUATING" }] })}\n\n` });
      if (path === `/releases/${item.id}`) return json({
        id: item.id, environmentId: "environment-e2e", version: item.version, status: "ANALYZING", createdAt,
        imageRepository: "example.invalid/e2e", imageDigest: `sha256:${"a".repeat(64)}`, changeSummary: `Isolated ${item.id}`, commitSha: "e2e-commit", pipelineUrl: "https://example.invalid/e2e",
        context: { serviceName: item.serviceName, environmentName: "isolated-e2e" }, requester: { displayName: "E2E Requester", username: "e2e" },
        policySnapshot: { name: "E2E Policy", checksum: "e2e-checksum", definition: { strategy: "CANARY", steps: [{ weight: 10, minimumObservationSeconds: 60 }], metrics: [{ key: "HTTP_5XX_RATE", threshold: 0.01 }] } },
      });
    }
    unexpected.push(`Unhandled API: ${path}`);
    return json({ code: "UNHANDLED_FIXTURE" }, 500);
  });
  return unexpected;
}

async function login(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "읽기 전용 데모", exact: true }).click();
  await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
  await expect(page.getByRole("combobox", { name: "최근 릴리스" })).toContainText("v-e2e-a");
}

async function assertViewer(page: Page) {
  for (const action of ["Promote", "Pause", "Resume", "Abort"]) await expect(page.getByRole("button", { name: action, exact: true })).toBeDisabled();
  for (const action of ["Approve", "Reject", "다른 세션 모두 종료"]) await expect(page.getByRole("button", { name: action, exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "활성 세션" })).toContainText("공유 데모에서는");
}

test("sample state is explicit until an actual release is loaded", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await expect(page.getByRole("note")).toContainText("실제 운영 결과가 아닙니다");
  await expect(page.getByText("SAMPLE RELEASE · 예시", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  await expect(page.getByText("SELECTED RELEASE", { exact: true })).toBeVisible();
  await expect(page.getByRole("note")).toHaveCount(0);
  expect(unexpected).toEqual([]);
});

for (const width of [320, 390, 800, 1024]) {
  test(`navigation and release controls fit at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const unexpected = await isolateApi(page);
    await login(page);
    const nav = page.getByRole("navigation");
    const brand = await nav.getByText("ReleasePilot").boundingBox();
    const demo = await nav.getByRole("button", { name: "읽기 전용 데모", exact: true }).boundingBox();
    expect(brand).toBeTruthy();
    expect(demo).toBeTruthy();
    expect(brand!.y + brand!.height <= demo!.y || brand!.x + brand!.width <= demo!.x).toBe(true);
    const load = page.getByRole("button", { name: "불러오기", exact: true });
    expect((await load.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    await expect(load).toBeEnabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/ui-${width}.png`, fullPage: true });
    expect(unexpected).toEqual([]);
  });
}

test("production CSP authorizes bootstrap with fresh nonces", async ({ page, request }) => {
  const unexpected = await isolateApi(page);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (/Content Security Policy|Refused to execute/i.test(message.text())) errors.push(message.text()); });
  const response = await page.goto("/");
  const policy = response!.headers()["content-security-policy"];
  const nonce = policy.match(/'nonce-([^']+)'/)?.[1];
  expect(nonce).toBeTruthy();
  const scriptPolicy = policy.split(";").find((directive) => directive.trim().startsWith("script-src"))!;
  expect(scriptPolicy).not.toMatch(/unsafe-inline|unsafe-eval/);
  expect(response!.headers()["cache-control"]).toContain("no-store");
  const scriptNonces = await page.locator("script").evaluateAll((scripts) => scripts.map((script) => (script as HTMLScriptElement).nonce));
  expect(scriptNonces.length).toBeGreaterThan(0);
  expect(scriptNonces.every((value) => value === nonce)).toBe(true);
  const second = await request.get("/");
  expect(second.headers()["content-security-policy"].match(/'nonce-([^']+)'/)?.[1]).not.toBe(nonce);
  await page.getByRole("button", { name: "읽기 전용 데모", exact: true }).click();
  await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
  expect(errors).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("demo session survives reload with the same read-only banner", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await assertViewer(page);
  await page.reload();
  await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
  await expect(page.getByRole("navigation")).not.toContainText("DEMO SNAPSHOT");
  await expect(page.getByRole("button", { name: "최근 릴리스 새로고침" })).toBeEnabled();
  await assertViewer(page);
  expect(unexpected).toEqual([]);
});

test("release selection updates context, evidence and audit without enabling writes", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  for (const item of fixtures) {
    await page.getByRole("combobox", { name: "최근 릴리스" }).selectOption(item.id);
    await page.getByRole("button", { name: "불러오기", exact: true }).click();
    await expect(page.getByRole("heading", { name: `release · ${item.version}`, exact: true })).toBeVisible();
    await expect(page.getByRole("region", { name: "릴리스 승인 컨텍스트" })).toContainText(`${item.serviceName} · isolated-e2e`);
    await expect(page.getByText(`${item.hash.slice(0, 12)}…`, { exact: true })).toBeVisible();
    const audit = page.getByRole("region", { name: "릴리스 변경 기록" });
    await expect(audit).toContainText(item.eventType === "RELEASE_APPROVED" ? "Release Approved" : "Release Aborted");
    await expect(audit).toContainText(`chain #${item.sequence}`);
    await assertViewer(page);
  }
  await expect(page.getByText("aaaaaaaaaaaa…", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).not.toContainText("Release Approved");
  expect(unexpected).toEqual([]);
});

test("denied release does not leave the previous target actionable", async ({ page }) => {
  const unexpected = await isolateApi(page, "release-b");
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "release · v-e2e-a", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "최근 릴리스" }).selectOption("release-b");
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  await expect(page.getByText("릴리스 조회 권한 또는 ID를 확인하세요.", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "release · v-e2e-a", exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "release · v-e2e-b", exact: true })).toHaveCount(0);
  await assertViewer(page);
  expect(unexpected).toEqual([]);
});
