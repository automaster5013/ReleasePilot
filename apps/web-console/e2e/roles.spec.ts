import { expect, Page, test } from "@playwright/test";

const origin = "http://127.0.0.1:3100";
const serviceId = "11111111-1111-4111-8111-111111111111";
const environmentId = "22222222-2222-4222-8222-222222222222";

for (const width of [320, 390]) {
  for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
    test(`${role} mobile flow fits and remains actionable at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      const state = await fixture(page, role);
      if (role === "DEVELOPER") await fillRequest(page);
      else await load(page);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const names = role === "DEVELOPER" ? ["릴리스 요청"] : role === "APPROVER" ? ["Approve", "Reject"] : ["Promote", "Pause", "Resume", "Abort"];
      for (const name of names) {
        const button = page.getByRole("button", { name, exact: true });
        await expect(button).toBeEnabled();
        await button.scrollIntoViewIfNeeded();
        const box = await button.boundingBox();
        expect(box).toBeTruthy();
        expect(box!.x).toBeGreaterThanOrEqual(0);
        expect(box!.x + box!.width).toBeLessThanOrEqual(width);
      }
      await page.screenshot({ path: `test-results/role-${role}-${width}.png`, fullPage: true });
      if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Mobile fixture reason"));
      await page.getByRole("button", { name: names[0], exact: true }).click();
      if (role === "DEVELOPER") await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
      else await expect(page.getByRole("status")).toContainText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다.");
      expect(state.mutations).toHaveLength(1);
      expect(state.unexpected).toEqual([]);
    });
  }
}
type Mutation = { path: string; body: Record<string, string | null> };

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
  for (const csrfBody of [null, {}, { headerName: "X-CSRF-TOKEN", token: " " }]) {
    test(`${role} blocks malformed CSRF ${JSON.stringify(csrfBody)}`, async ({ page }) => {
      const state = await fixture(page, role, { csrfBody });
      if (role === "DEVELOPER") await fillRequest(page);
      else await load(page);
      const button = page.getByRole("button", { name: role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort", exact: true });
      await expect(button).toBeEnabled();
      if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Malformed token fixture"));
      await button.click();
      await expect(page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." })).toBeVisible();
      await expect(button).toBeEnabled();
      expect(state.mutations).toEqual([]);
      expect(state.unexpected).toEqual([]);
    });
  }
}

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
  test(`${role} retries after a CSRF HTTP failure without an initial mutation`, async ({ page }) => {
    const state = await fixture(page, role, { csrfRejectOnce: true });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const button = page.getByRole("button", { name: role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort", exact: true });
    await expect(button).toBeEnabled();
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Token recovery fixture"));
    await button.click();
    const failure = page.getByRole("alert").filter({ hasText: "보안 토큰을 갱신할 수 없습니다." });
    await expect(failure).toBeVisible();
    await expect(button).toBeEnabled();
    expect(state.mutations).toEqual([]);
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Token recovery fixture"));
    await button.click();
    if (role === "DEVELOPER") await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
    else await expect(page.getByRole("status")).toContainText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다.");
    await expect(failure).toHaveCount(0);
    expect(state.mutations).toHaveLength(1);
    await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).toContainText("chain #1");
    expect(state.unexpected).toEqual([]);
  });
}

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
  for (const csrfStatus of [0, 401, 403, 500]) {
  test(`${role} does not mutate when CSRF retrieval fails with ${csrfStatus || "connection failure"}`, async ({ page }) => {
    const state = await fixture(page, role, { csrfFailure: csrfStatus === 0, csrfStatus });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const button = page.getByRole("button", { name: role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort", exact: true });
    await expect(button).toBeEnabled();
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("CSRF failure fixture"));
    await button.click();
    await expect(page.getByRole("alert").filter({ hasText: csrfStatus === 0 ? "Failed to fetch" : "보안 토큰을 갱신할 수 없습니다." })).toBeVisible();
    await expect(button).toBeEnabled();
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
    await expect(page.getByText(/승인 결정이 기록되었습니다\.|abort 요청이 접수되었습니다\.|요청이 생성되었습니다/)).toHaveCount(0);
  });
  }
}

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
  test(`${role} can retry successfully after the first connection failure`, async ({ page }) => {
    const state = await fixture(page, role, { disconnectOnce: true });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const name = role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort";
    const button = page.getByRole("button", { name, exact: true });
    await expect(button).toBeEnabled();
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Retry fixture reason"));
    await button.click();
    const failure = page.getByRole("alert").filter({ hasText: "Failed to fetch" });
    await expect(failure).toBeVisible();
    await expect(button).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Retry fixture reason"));
    await button.click();
    if (role === "DEVELOPER") await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
    else await expect(page.getByRole("status")).toContainText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다.");
    await expect(failure).toHaveCount(0);
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).toContainText("chain #1");
    expect(state.unexpected).toEqual([]);
  });
}

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"]) {
  test(`${role} recovers controls after a mutation connection failure`, async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const state = await fixture(page, role, { disconnect: true });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const name = role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort";
    const button = page.getByRole("button", { name, exact: true });
    await expect(button).toBeEnabled();
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Connection failure fixture"));
    await button.click();
    await expect(page.getByRole("alert").filter({ hasText: "Failed to fetch" })).toBeVisible();
    await expect(button).toBeEnabled();
    await expect(page.getByText(/승인 결정이 기록되었습니다\.|abort 요청이 접수되었습니다\.|요청이 생성되었습니다/)).toHaveCount(0);
    if (role === "DEVELOPER") await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toHaveCount(0);
    else await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).not.toContainText("chain #1");
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
    expect(errors).toEqual([]);
  });
}

for (const rejected of [false, true]) {
  test(`developer request remains locked until ${rejected ? "rejection" : "creation"}`, async ({ page }) => {
    let respond!: () => void;
    const responseGate = new Promise<void>((resolve) => { respond = resolve; });
    const state = await fixture(page, "DEVELOPER", { responseGate, failure: rejected ? "FORBIDDEN" : undefined });
    try {
      await fillRequest(page);
      const button = page.getByRole("button", { name: "릴리스 요청", exact: true });
      await button.click();
      await expect.poll(() => state.mutations.length).toBe(1);
      const pending = page.getByRole("button", { name: "요청 중…", exact: true });
      await expect(pending).toBeDisabled();
      await pending.evaluate((element) => (element as HTMLButtonElement).click());
      await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toHaveCount(0);
      expect(state.mutations).toHaveLength(1);
      respond();
      if (rejected) {
        await expect(page.getByRole("alert").filter({ hasText: "릴리스 요청이 거부되었습니다." })).toBeVisible();
        await expect(button).toBeEnabled();
        await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toHaveCount(0);
      } else {
        await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
        await expect(button).toBeEnabled();
      }
      expect(state.mutations).toHaveLength(1);
      expect(state.unexpected).toEqual([]);
    } finally { respond(); }
  });
}

for (const role of ["APPROVER", "OPERATOR"]) {
  for (const rejected of [false, true]) {
  test(`${role} prevents repeated actions while awaiting ${rejected ? "rejection" : "success"}`, async ({ page }) => {
    let respond!: () => void;
    const responseGate = new Promise<void>((resolve) => { respond = resolve; });
    const state = await fixture(page, role, { responseGate, failure: rejected ? "FORBIDDEN" : undefined });
    try {
      await load(page);
      const names = role === "APPROVER" ? ["Approve", "Reject"] : ["Promote", "Pause", "Resume", "Abort"];
      const button = page.getByRole("button", { name: names[0], exact: true });
      await expect(button).toBeEnabled();
      page.once("dialog", (dialog) => dialog.accept("Pending fixture reason"));
      await button.click();
      await expect.poll(() => state.mutations.length).toBe(1);
      for (const name of names) await expect(page.getByRole("button", { name, exact: true })).toBeDisabled();
      // A native click on a disabled control must not initiate another mutation.
      await button.evaluate((element) => (element as HTMLButtonElement).click());
      expect(state.mutations).toHaveLength(1);
      await expect(page.getByText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다.", { exact: true })).toHaveCount(0);
      respond();
      if (rejected) {
        await expect(page.getByRole("alert").filter({ hasText: role === "APPROVER" ? "approve 요청이 거부되었습니다." : "promote 요청이 거부되었습니다." })).toBeVisible();
        for (const name of names) await expect(page.getByRole("button", { name, exact: true })).toBeEnabled();
        await expect(page.getByText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다.", { exact: true })).toHaveCount(0);
        await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).not.toContainText("chain #1");
      } else {
      await expect(page.getByRole("status")).toContainText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다.");
      if (role === "OPERATOR") for (const name of names) await expect(page.getByRole("button", { name, exact: true })).toBeEnabled();
      else await expect(button).toHaveCount(0);
      }
      expect(state.mutations).toHaveLength(1);
      expect(state.unexpected).toEqual([]);
    } finally {
      respond();
    }
  });
  }
}

for (const action of ["approve", "reject", "promote", "pause", "resume", "abort"]) {
  test(`${action} accepts a single character reason after whitespace trimming`, async ({ page }) => {
    const decision = action === "approve" || action === "reject";
    const state = await fixture(page, decision ? "APPROVER" : "OPERATOR");
    await load(page);
    const button = page.getByRole("button", { name: action[0].toUpperCase() + action.slice(1), exact: true });
    await expect(button).toBeEnabled();
    page.once("dialog", (dialog) => dialog.accept(" \t가\n "));
    await button.click();
    const notice = decision ? (action === "approve" ? "승인 결정이 기록되었습니다." : "거부 결정이 기록되었습니다.") : `${action} 요청이 접수되었습니다.`;
    await expect(page.getByRole("status")).toContainText(notice);
    expect(state.mutations).toEqual([{ path: `/releases/role-release/${action}`, body: { reason: "가" } }]);
    await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).toContainText("chain #1");
    expect(state.unexpected).toEqual([]);
  });
}

for (const role of ["APPROVER", "OPERATOR"]) {
  test(`${role} accepts the 1000 character reason boundary`, async ({ page }) => {
    const state = await fixture(page, role);
    await load(page);
    const reason = "가".repeat(1000);
    const action = role === "APPROVER" ? "approve" : "abort";
    const button = page.getByRole("button", { name: role === "APPROVER" ? "Approve" : "Abort", exact: true });
    await expect(button).toBeEnabled();
    page.once("dialog", (dialog) => dialog.accept(` ${reason} `));
    await button.click();
    await expect(page.getByRole("status")).toContainText(role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다.");
    expect(state.mutations).toEqual([{ path: `/releases/role-release/${action}`, body: { reason } }]);
    expect(state.unexpected).toEqual([]);
  });
}

async function fixture(page: Page, role: string, options: { stale?: boolean; failure?: string; responseGate?: Promise<void>; disconnect?: boolean; disconnectOnce?: boolean; csrfFailure?: boolean; csrfStatus?: number; csrfRejectOnce?: boolean; csrfBody?: unknown } = {}) {
  let status = role === "OPERATOR" ? "ANALYZING" : "PENDING_APPROVAL";
  const mutations: Mutation[] = [];
  const unexpected: string[] = [];
  const audit: object[] = [];
  let csrfRequests = 0;
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== origin) { unexpected.push(`External request: ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/control-api/")) return route.continue();
    const path = url.pathname.slice("/control-api".length);
    const json = (body: unknown, code = 200) => route.fulfill({ status: code, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() !== "GET") {
      const permitted = request.method() === "POST" && (
        (role === "DEVELOPER" && path === "/releases") ||
        (role === "APPROVER" && /^\/releases\/role-release\/(approve|reject)$/.test(path)) ||
        (role === "OPERATOR" && /^\/releases\/role-release\/(promote|pause|resume|abort)$/.test(path))
      );
      if (!permitted || request.headers()["x-csrf-token"] !== "role-fixture-csrf" ||
          !request.headers()["idempotency-key"] || !request.headers()["content-type"]?.includes("application/json")) {
        unexpected.push(`Unsafe mutation: ${request.method()} ${path}`);
        return json({ code: "FORBIDDEN" }, 403);
      }
      mutations.push({ path, body: request.postDataJSON() });
      if (options.responseGate) await options.responseGate;
      if (options.disconnect || (options.disconnectOnce && mutations.length === 1)) return route.abort("connectionfailed");
      if (options.failure) return json({ code: options.failure }, 403);
      status = path.endsWith("/approve") ? "APPROVED" : path.endsWith("/reject") ? "REJECTED" : status;
      const action = path.split("/").at(-1)!;
      const eventType = path === "/releases" ? "RELEASE_REQUESTED" : ["approve", "reject"].includes(action)
        ? `RELEASE_${action === "approve" ? "APPROVED" : "REJECTED"}` : `ROLLOUT_${action.toUpperCase()}_REQUESTED`;
      audit.push({ id: "role-audit", eventType,
        actorType: "USER", actorId: "role-user", occurredAt: new Date().toISOString(), correlationId: "role-correlation", chainSequence: 1 });
      return json({ id: "role-release", status }, path === "/releases" ? 201 : 200);
    }
    if (path === "/session/providers") return json({ oidc: false, loginUrl: null });
    if (path === "/session") return json({ user: { id: "role-user", displayName: "Role fixture", roles: [role], demo: false }, csrfToken: "role-fixture-csrf", expiresAt: "2099-01-01T00:00:00Z" });
    if (path === "/session/csrf") {
      if ("csrfBody" in options) return json(options.csrfBody);
      csrfRequests++;
      return options.csrfFailure ? route.abort("connectionfailed") : json({ headerName: "X-CSRF-TOKEN", token: "role-fixture-csrf" }, options.csrfRejectOnce && csrfRequests === 1 ? 403 : options.csrfStatus || 200);
    }
    if (path === "/session/active") return json({ items: [] });
    if (path === "/audit-events/verify") return json({ valid: true, verifiedEvents: audit.length, failedEventId: null, headHash: "a".repeat(64) });
    if (path === "/audit-events") return json({ items: audit });
    if (path === "/connections/clusters" || path === "/connections/prometheus") return json([]);
    if (path === "/projects") return json({ items: [{ id: "project-role", name: "Role project", key: "role", status: "ACTIVE" }] });
    if (path === "/projects/project-role/services") return json({ items: [{ id: serviceId, name: "Role service", key: "role", status: "ACTIVE" }] });
    if (path === `/services/${serviceId}/environments`) return json({ items: [{ id: environmentId, name: "Role environment", status: "ACTIVE", strategy: "CANARY" }] });
    if (path === `/environments/${environmentId}/validation-results/latest`) return json({ environmentId, status: "ACTIVE", checkedAt: new Date().toISOString(), validUntil: options.stale ? "2020-01-01T00:00:00Z" : "2099-01-01T00:00:00Z", checks: [{ code: "FIXTURE_READY", outcome: "PASS", message: "Isolated readiness" }] });
    if (path === "/releases") return json({ items: [{ id: "role-release", version: "v-role", status, createdAt: new Date().toISOString(), context: { serviceName: "Role service", environmentName: "Role environment" } }] });
    if (path === "/releases/role-release/analyses") return json([]);
    if (path === "/releases/role-release/events") return route.fulfill({ contentType: "text/event-stream", body: `event: release-state\ndata: ${JSON.stringify({ releaseStatus: status, steps: [] })}\n\n` });
    if (path === "/releases/role-release") return json({ id: "role-release", environmentId, version: "v-role", status, createdAt: new Date().toISOString(), imageRepository: "example.invalid/role", imageDigest: `sha256:${"a".repeat(64)}`, changeSummary: "Role fixture", commitSha: "a".repeat(40), pipelineUrl: "https://example.invalid/role", context: { serviceName: "Role service", environmentName: "Role environment" }, requester: { displayName: "Other requester", username: "other" }, policySnapshot: { name: "Role policy", checksum: "role-policy", definition: { strategy: "CANARY", steps: [], metrics: [] } } });
    unexpected.push(`Unhandled API: ${path}`);
    return json({ code: "UNHANDLED_FIXTURE" }, 500);
  });
  return { mutations, unexpected };
}

async function load(page: Page) {
  await page.goto("/");
  await page.getByRole("combobox", { name: "최근 릴리스" }).selectOption("role-release");
  await page.getByRole("button", { name: "불러오기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
}

async function fillRequest(page: Page) {
  await page.goto("/");
  await page.getByText("NEW RELEASE REQUEST", { exact: false }).click();
  await page.getByRole("combobox", { name: /^Project/ }).selectOption("project-role");
  await page.getByRole("combobox", { name: /^Service/ }).selectOption(serviceId);
  await page.getByRole("combobox", { name: /^Environment/ }).selectOption(environmentId);
  await expect(page.getByText("Isolated readiness", { exact: true })).toBeVisible();
  await page.getByLabel("Version", { exact: true }).fill("v-role");
  await page.getByLabel("Image repository", { exact: true }).fill("example.invalid/role");
  await page.getByLabel("Image digest", { exact: true }).fill(`sha256:${"a".repeat(64)}`);
  await page.getByLabel("Change summary", { exact: true }).fill("Role request");
  await page.getByLabel("Commit SHA", { exact: true }).fill("a".repeat(40));
  await page.getByLabel("Pipeline URL", { exact: true }).fill("https://example.invalid/role");
}

test("developer submits selected catalog targets and loads the created release", async ({ page }) => {
  const state = await fixture(page, "DEVELOPER");
  await fillRequest(page);
  await page.getByRole("button", { name: "릴리스 요청", exact: true }).click();
  await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
  expect(state.mutations).toHaveLength(1);
  expect(state.mutations[0].body).toMatchObject({ serviceId, environmentId, version: "v-role", requestedPolicyVersionId: null });
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Promote", exact: true })).toBeDisabled();
  expect(state.unexpected).toEqual([]);
});

test("developer cannot submit against expired environment readiness", async ({ page }) => {
  const state = await fixture(page, "DEVELOPER", { stale: true });
  await fillRequest(page);
  await expect(page.getByRole("button", { name: "릴리스 요청", exact: true })).toBeDisabled();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

for (const action of ["approve", "reject"] as const) {
  test(`approver records ${action} with reason and refreshes audit`, async ({ page }) => {
    const state = await fixture(page, "APPROVER");
    await load(page);
    const button = page.getByRole("button", { name: action === "approve" ? "Approve" : "Reject", exact: true });
    await expect(button).toBeEnabled();
    page.once("dialog", (dialog) => dialog.accept(" Role decision "));
    await button.click();
    await expect(page.getByRole("status")).toContainText(action === "approve" ? "승인 결정이 기록되었습니다." : "거부 결정이 기록되었습니다.");
    expect(state.mutations).toEqual([{ path: `/releases/role-release/${action}`, body: { reason: "Role decision" } }]);
    await expect(page.getByRole("region", { name: "릴리스 변경 기록" })).toContainText("chain #1");
    await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
    expect(state.unexpected).toEqual([]);
  });
}

test("self approval rejection preserves pending approval controls", async ({ page }) => {
  const state = await fixture(page, "APPROVER", { failure: "SELF_APPROVAL_NOT_ALLOWED" });
  await load(page);
  const approve = page.getByRole("button", { name: "Approve", exact: true });
  await expect(approve).toBeEnabled();
  page.once("dialog", (dialog) => dialog.accept("Role decision"));
  await approve.click();
  await expect(page.getByRole("alert").filter({ hasText: "요청자는 자신의 릴리스를 승인할 수 없습니다." })).toBeVisible();
  await expect(approve).toBeEnabled();
  expect(state.mutations).toHaveLength(1);
  expect(state.unexpected).toEqual([]);
});

test("expired approval readiness blocks approval while allowing rejection", async ({ page }) => {
  const state = await fixture(page, "APPROVER", { stale: true });
  await load(page);
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Reject", exact: true })).toBeEnabled();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

for (const action of ["promote", "pause", "resume", "abort"]) {
  test(`operator submits ${action} with reason and safe headers`, async ({ page }) => {
    const state = await fixture(page, "OPERATOR");
    await load(page);
    const button = page.getByRole("button", { name: action[0].toUpperCase() + action.slice(1), exact: true });
    await expect(button).toBeEnabled();
    page.once("dialog", (dialog) => dialog.accept(" Role operation "));
    await button.click();
    await expect(page.getByRole("status")).toContainText(`${action} 요청이 접수되었습니다.`);
    expect(state.mutations).toEqual([{ path: `/releases/role-release/${action}`, body: { reason: "Role operation" } }]);
    expect(state.unexpected).toEqual([]);
  });
}

test("cancelled operator reason does not transmit a mutation", async ({ page }) => {
  const state = await fixture(page, "OPERATOR");
  await load(page);
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("button", { name: "Abort", exact: true }).click();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("developer sees server readiness rejection without a created release", async ({ page }) => {
  const state = await fixture(page, "DEVELOPER", { failure: "ENVIRONMENT_VALIDATION_STALE" });
  await fillRequest(page);
  await page.getByRole("button", { name: "릴리스 요청", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "환경 검증이 만료되었습니다." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toHaveCount(0);
  await expect(page.getByText(/요청이 생성되었습니다/)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "릴리스 요청", exact: true })).toBeEnabled();
  expect(state.mutations).toHaveLength(1);
  expect(state.unexpected).toEqual([]);
});

test("approver sees server readiness rejection and retains decision controls", async ({ page }) => {
  const state = await fixture(page, "APPROVER", { failure: "ENVIRONMENT_VALIDATION_STALE" });
  await load(page);
  const approve = page.getByRole("button", { name: "Approve", exact: true });
  await expect(approve).toBeEnabled();
  page.once("dialog", (dialog) => dialog.accept("Role decision"));
  await approve.click();
  await expect(page.getByRole("alert").filter({ hasText: "환경 검증이 만료되어 승인할 수 없습니다." })).toBeVisible();
  await expect(approve).toBeEnabled();
  await expect(page.getByText("승인 결정이 기록되었습니다.", { exact: true })).toHaveCount(0);
  expect(state.mutations).toHaveLength(1);
  expect(state.unexpected).toEqual([]);
});

test("operator rejection does not claim acceptance and releases the busy state", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { failure: "FORBIDDEN" });
  await load(page);
  const abort = page.getByRole("button", { name: "Abort", exact: true });
  page.once("dialog", (dialog) => dialog.accept("Role operation"));
  await abort.click();
  await expect(page.getByRole("alert").filter({ hasText: "abort 요청이 거부되었습니다." })).toBeVisible();
  await expect(abort).toBeEnabled();
  await expect(page.getByText("abort 요청이 접수되었습니다.", { exact: true })).toHaveCount(0);
  expect(state.mutations).toHaveLength(1);
  expect(state.unexpected).toEqual([]);
});

for (const role of ["APPROVER", "OPERATOR"]) {
  for (const reason of ["   ", "x".repeat(1001)]) {
    test(`${role} rejects ${reason.length === 3 ? "blank" : "oversized"} reason before transmission`, async ({ page }) => {
      const state = await fixture(page, role);
      await load(page);
      const action = page.getByRole("button", { name: role === "APPROVER" ? "Reject" : "Abort", exact: true });
      page.once("dialog", (dialog) => dialog.accept(reason));
      await action.click();
      await expect(page.getByRole("alert").filter({ hasText: "1000자 이하여야 합니다." })).toBeVisible();
      await expect(action).toBeEnabled();
      expect(state.mutations).toEqual([]);
      expect(state.unexpected).toEqual([]);
    });
  }
}

test("cancelled approval reason does not transmit a mutation", async ({ page }) => {
  const state = await fixture(page, "APPROVER");
  await load(page);
  const approve = page.getByRole("button", { name: "Approve", exact: true });
  await expect(approve).toBeEnabled();
  page.once("dialog", (dialog) => dialog.dismiss());
  await approve.click();
  await expect(approve).toBeEnabled();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});
