import { expect, Page, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const origin = "http://127.0.0.1:3100";
const createdAt = "2026-09-15T00:00:00Z";

for (const failure of ["connection", "invalid-json"]) {
  test(`demo login recovers after POST ${failure}`, async ({ page }) => {
    const unexpected = await isolateApi(page);
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    let requests = 0;
    await page.route("**/control-api/session/demo", async (route) => {
      requests++;
      if (requests > 1) return route.fallback();
      if (failure === "connection") return route.abort("connectionfailed");
      return route.fulfill({ status: 200, contentType: "application/json", body: "{broken" });
    });
    await page.goto("/");
    const button = page.getByRole("button", { name: "읽기 전용 데모", exact: true });
    await button.click();
    const error = page.getByRole("alert").filter({ hasText: failure === "connection" ? "Failed to fetch" : /JSON/ });
    await expect(error).toBeVisible();
    await expect(button).toBeEnabled();
    await expect(page.getByRole("navigation")).not.toContainText("DEMO · VIEW ONLY");
    expect(requests).toBe(1);
    await button.click();
    await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
    await expect(button).toBeEnabled();
    await expect(error).toHaveCount(0);
    expect(requests).toBe(2);
    expect(errors).toEqual([]);
    expect(unexpected).toEqual([]);
  });
}

for (const rejected of [false, true]) {
  for (const stage of ["demo", "csrf"]) {
  test(`demo login locks controls while awaiting ${stage} ${rejected ? "rejection" : "success"}`, async ({ page }) => {
    const unexpected = await isolateApi(page);
    let respond!: () => void;
    const gate = new Promise<void>((resolve) => { respond = resolve; });
    let requests = 0;
    let demoRequests = 0;
    await page.route("**/control-api/session/demo", async (route) => { demoRequests++; await route.fallback(); });
    await page.route(`**/control-api/session/${stage}`, async (route) => {
      requests++;
      await gate;
      if (rejected) return route.fulfill({ status: 403, contentType: "application/json", body: "{}" });
      await route.fallback();
    });
    try {
      await page.goto("/");
      const button = page.getByRole("button", { name: "읽기 전용 데모", exact: true });
      await button.click();
      await expect.poll(() => requests).toBe(1);
      await expect(button).toBeDisabled();
      await button.evaluate((element) => (element as HTMLButtonElement).click());
      await expect(page.getByRole("navigation")).not.toContainText("DEMO · VIEW ONLY");
      expect(requests).toBe(1);
      if (stage === "csrf") expect(demoRequests).toBe(0);
      respond();
      if (rejected) await expect(page.getByRole("alert").filter({ hasText: stage === "demo" ? "공개 데모 세션을 시작할 수 없습니다." : "보안 토큰을 갱신할 수 없습니다." })).toBeVisible();
      else await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
      await expect(button).toBeEnabled();
      expect(requests).toBe(1);
      // Rejected token retrieval must never reach the login mutation.
      if (stage === "csrf") expect(demoRequests).toBe(rejected ? 0 : 1);
      expect(unexpected).toEqual([]);
    } finally { respond(); }
  });
  }
}

for (const failure of ["null", "empty", "forbidden", "connection"]) {
  test(`demo login blocks ${failure} CSRF and recovers on retry`, async ({ page }) => {
    const unexpected = await isolateApi(page);
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    let csrfRequests = 0;
    let demoRequests = 0;
    await page.route("**/control-api/session/demo", async (route) => { demoRequests++; await route.fallback(); });
    await page.route("**/control-api/session/csrf", async (route) => {
      csrfRequests++;
      if (csrfRequests > 1) return route.fallback();
      if (failure === "connection") return route.abort("connectionfailed");
      return route.fulfill({ status: failure === "forbidden" ? 403 : 200, contentType: "application/json", body: failure === "null" ? "null" : "{}" });
    });
    await page.goto("/");
    const button = page.getByRole("button", { name: "읽기 전용 데모", exact: true });
    await button.click();
    const message = failure === "connection" ? "Failed to fetch" : failure === "forbidden" ? "보안 토큰을 갱신할 수 없습니다." : "보안 토큰 응답이 올바르지 않습니다.";
    await expect(page.getByRole("alert").filter({ hasText: message })).toBeVisible();
    expect(demoRequests).toBe(0);
    await expect(page.getByRole("navigation")).not.toContainText("DEMO · VIEW ONLY");
    await button.click();
    await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
    await expect(page.getByRole("alert").filter({ hasText: message })).toHaveCount(0);
    expect(demoRequests).toBe(1);
    expect(errors).toEqual([]);
    expect(unexpected).toEqual([]);
  });
}
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
    if (request.method() === "POST" && path === "/session/logout") {
      if (request.headers()["x-csrf-token"] !== "test-only-csrf") {
        unexpected.push("Missing logout CSRF header");
        return json({ code: "CSRF_INVALID" }, 403);
      }
      authenticated = false;
      return route.fulfill({ status: 204 });
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

test("logout clears the session and selected release after navigation", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page.getByRole("navigation")).toContainText("DEMO SNAPSHOT");
  await expect(page.getByRole("button", { name: "로그아웃", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "최근 릴리스 새로고침" })).toBeDisabled();
  await page.reload();
  await expect(page.getByRole("navigation")).toContainText("DEMO SNAPSHOT");
  expect(unexpected).toEqual([]);
});

test("rejected logout preserves the session and permits a manual retry", async ({ page }) => {
  const unexpected = await isolateApi(page);
  let attempts = 0;
  await page.route("**/control-api/session/logout", async (route) => {
    attempts++;
    if (attempts === 1) return route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ code: "FORBIDDEN" }) });
    await route.fallback();
  });
  await login(page);
  const button = page.getByRole("button", { name: "로그아웃", exact: true });
  await button.click();
  await expect(page.getByRole("alert").filter({ hasText: "로그아웃하지 못했습니다. 다시 시도해 주세요." })).toBeVisible();
  await expect(button).toBeEnabled();
  await expect(page.getByRole("navigation")).toContainText("DEMO · VIEW ONLY");
  expect(attempts).toBe(1);
  await button.click();
  await expect(page.getByRole("navigation")).toContainText("DEMO SNAPSHOT");
  await expect(button).toHaveCount(0);
  expect(attempts).toBe(2);
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

test("@a11y viewer screen meets automated WCAG A and AA checks", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  expect(results.violations.map(({ id, nodes }) => ({ id, targets: nodes.map((node) => node.target) }))).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer primary controls are reachable with visible keyboard focus", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await reachByTab(page, "최근 릴리스", "SELECT");
  await reachByTab(page, "불러오기", "BUTTON");
  expect(unexpected).toEqual([]);
});

test("@a11y viewer high contrast mode reflows without clipping controls", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  await page.emulateMedia({ forcedColors: "active" });
  const unexpected = await isolateApi(page);
  await login(page);
  await expect(page.getByRole("combobox", { name: "최근 릴리스" })).toBeVisible();
  await expect(page.getByRole("button", { name: "불러오기", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await reachByTab(page, "불러오기", "BUTTON");
  expect(await page.getByRole("button", { name: "불러오기", exact: true }).evaluate((element) => element.matches(":focus-visible"))).toBe(true);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer supports WCAG text spacing at 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  const unexpected = await isolateApi(page);
  await login(page);
  await page.addStyleTag({ content: `
    * { line-height: 1.5 !important; letter-spacing: .12em !important; word-spacing: .16em !important; }
    p { margin-bottom: 2em !important; }
  ` });
  const loadButton = page.getByRole("button", { name: "불러오기", exact: true });
  await expect(page.getByRole("combobox", { name: "최근 릴리스" })).toBeVisible();
  await expect(loadButton).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await loadButton.scrollIntoViewIfNeeded();
  const box = await loadButton.boundingBox();
  expect(box).toBeTruthy();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(320);
  expect(await loadButton.evaluate((element) => element.scrollWidth <= element.clientWidth + 1 && element.scrollHeight <= element.clientHeight + 1)).toBe(true);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer honors reduced motion preferences", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  const motion = await page.evaluate(() => {
    const elements = [document.documentElement, ...Array.from(document.querySelectorAll("body *"))];
    return {
      preference: matchMedia("(prefers-reduced-motion: reduce)").matches,
      rootScrollBehavior: getComputedStyle(document.documentElement).scrollBehavior,
      offenders: elements.flatMap((element) => {
        const style = getComputedStyle(element);
        const animation = style.animationName !== "none" && parseFloat(style.animationDuration) > 0.01;
        const transition = style.transitionProperty !== "none" && parseFloat(style.transitionDuration) > 0.01;
        return animation || transition ? [element.tagName] : [];
      }),
    };
  });
  expect(motion.preference).toBe(true);
  expect(motion.rootScrollBehavior).toBe("auto");
  expect(motion.offenders).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer pointer targets meet the 24px minimum at 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  const undersized = await page.locator("button, input, select, a[href]").evaluateAll((elements) =>
    elements.flatMap((element) => {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      const visible = style.visibility !== "hidden" && style.display !== "none" && rect.width > 0 && rect.height > 0;
      const inlineLink = element.tagName === "A" && style.display === "inline";
      if (!visible || inlineLink || (rect.width >= 24 && rect.height >= 24)) return [];
      return [{
        element: element.tagName,
        name: element.getAttribute("aria-label") || element.textContent?.trim() || element.getAttribute("name") || "",
        width: Math.round(rect.width * 10) / 10,
        height: Math.round(rect.height * 10) / 10,
      }];
    }),
  );
  expect(undersized).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer keyboard focus is not obscured at 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  const result = await inspectTabFocusVisibility(page, 80);
  expect(result.visited).toBeGreaterThan(0);
  expect(result.obscured).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer keyboard focus indicator meets WCAG 2.4.13 minimum", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  const unexpected = await isolateApi(page);
  await login(page);
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  const result = await inspectTabFocusAppearance(page, 80);
  expect(result.visited).toBeGreaterThan(0);
  expect(result.failures).toEqual([]);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer can bypass repeated navigation with the skip link", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await page.evaluate(() => {
    document.body.tabIndex = -1;
    document.body.focus();
  });
  await page.keyboard.press("Tab");
  const skipLink = page.locator('a[href="#main-content"]');
  await expect(skipLink).toHaveAccessibleName("본문으로 건너뛰기");
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page.locator("#main-content")).toBeFocused();
  expect(await page.evaluate(() => location.hash)).toBe("#main-content");
  expect(unexpected).toEqual([]);
});

test("@a11y viewer exposes a titled landmark hierarchy", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  await expect(page).toHaveTitle("ReleasePilot — Safe delivery control plane");
  await expect(page.getByRole("main")).toHaveAccessibleName("Progressive delivery control room");
  await expect(page.getByRole("navigation", { name: "주요 탐색 및 계정 제어" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 1, name: "Progressive delivery control room" })).toHaveCount(1);
  expect(unexpected).toEqual([]);
});

test("@a11y viewer announces session connection status without moving focus", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  const status = page.locator("span[role=status][aria-live=polite][aria-atomic=true]");
  await expect(status).toHaveText("DEMO · VIEW ONLY");
  await expect(status.locator("i")).toHaveAttribute("aria-hidden", "true");
  expect(unexpected).toEqual([]);
});

test("@a11y viewer status messages use a consistent polite atomic contract", async ({ page }) => {
  const unexpected = await isolateApi(page);
  await login(page);
  const statuses = page.locator('[role="status"]');
  expect(await statuses.count()).toBeGreaterThan(0);
  expect(await statuses.evaluateAll((elements) => elements.filter((element) => element.getAttribute("aria-live") !== "polite" || element.getAttribute("aria-atomic") !== "true").map((element) => element.textContent?.trim()))).toEqual([]);
  expect(unexpected).toEqual([]);
});

async function inspectTabFocusAppearance(page: Page, limit: number) {
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  const seen = new Set<string>();
  const failures: Array<{ name: string; tag: string; width: number; contrast: number }> = [];
  for (let index = 0; index < limit; index++) {
    await page.keyboard.press("Tab");
    const result = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null;
      if (!element || element === document.body) return null;
      const focusable = Array.from(document.querySelectorAll<HTMLElement>('a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])'));
      const fingerprint = `${element.tagName}:${focusable.indexOf(element)}:${element.id}`;
      const style = getComputedStyle(element);
      const parse = (color: string) => (color.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
      const luminance = (color: string) => {
        const channels = parse(color).map((value) => value / 255).map((value) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
      };
      const foreground = luminance(style.outlineColor);
      const background = luminance(getComputedStyle(document.body).backgroundColor);
      const contrast = (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
      return {
        fingerprint,
        name: (element.getAttribute("aria-label") || element.textContent || element.getAttribute("name") || "").trim(),
        tag: element.tagName,
        visible: element.matches(":focus-visible") && style.outlineStyle !== "none",
        width: parseFloat(style.outlineWidth),
        contrast: Math.round(contrast * 100) / 100,
      };
    });
    if (!result || seen.has(result.fingerprint)) break;
    seen.add(result.fingerprint);
    if (!result.visible || result.width < 2 || result.contrast < 3) failures.push(result);
  }
  return { visited: seen.size, failures };
}

async function reachByTab(page: Page, accessibleName: string, tagName: string) {
  await page.locator("body").focus();
  for (let index = 0; index < 40; index++) {
    await page.keyboard.press("Tab");
    const active = await page.evaluate(() => ({
      name: (document.activeElement?.getAttribute("aria-label") || document.activeElement?.textContent || "").trim(),
      tag: document.activeElement?.tagName || "",
      outline: getComputedStyle(document.activeElement as Element).outlineStyle,
    }));
    if (active.tag === tagName && active.name.includes(accessibleName)) {
      expect(active.outline).not.toBe("none");
      return;
    }
  }
  throw new Error(`Keyboard focus did not reach ${accessibleName}`);
}

async function inspectTabFocusVisibility(page: Page, limit: number) {
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  const seen = new Set<string>();
  const obscured: Array<{ name: string; tag: string; reason: string }> = [];
  for (let index = 0; index < limit; index++) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(500);
    const result = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null;
      if (!element || element === document.body) return null;
      const rect = element.getBoundingClientRect();
      const name = (element.getAttribute("aria-label") || element.textContent || element.getAttribute("name") || "").trim();
      const focusable = Array.from(document.querySelectorAll<HTMLElement>('a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])'));
      const fingerprint = `${element.tagName}:${focusable.indexOf(element)}:${element.id}`;
      const left = Math.max(0, rect.left);
      const right = Math.min(innerWidth, rect.right);
      const top = Math.max(0, rect.top);
      const bottom = Math.min(innerHeight, rect.bottom);
      if (right <= left || bottom <= top) return { fingerprint, name, tag: element.tagName, visible: false, topmost: false };
      const topmost = document.elementFromPoint((left + right) / 2, (top + bottom) / 2);
      return {
        fingerprint,
        name,
        tag: element.tagName,
        visible: true,
        topmost: Boolean(topmost && (topmost === element || element.contains(topmost))),
      };
    });
    if (!result) break;
    if (seen.has(result.fingerprint)) break;
    seen.add(result.fingerprint);
    if (!result.visible || !result.topmost) {
      obscured.push({ name: result.name, tag: result.tag, reason: result.visible ? "covered" : "outside viewport" });
    }
  }
  return { visited: seen.size, obscured };
}
