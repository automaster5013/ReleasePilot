import { expect, Page, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

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
      else await expect(page.getByRole("status").filter({ hasText: role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다." })).toBeVisible();
      expect(state.mutations).toHaveLength(1);
      expect(state.unexpected).toEqual([]);
    });
  }
}
type Mutation = { path: string; body: unknown };

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
    else await expect(page.getByRole("status").filter({ hasText: role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다." })).toBeVisible();
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
    else await expect(page.getByRole("status").filter({ hasText: role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다." })).toBeVisible();
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

test("release requests remain single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "DEVELOPER", { responseGate, failure: "FORBIDDEN" });
  try {
    await fillRequest(page);
    const request = page.getByRole("button", { name: "릴리스 요청", exact: true });
    await expect(request).toBeEnabled();

    await request.evaluate((button) => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations).toEqual([{
      path: "/releases",
      body: {
        serviceId,
        environmentId,
        version: "v-role",
        imageRepository: "example.invalid/role",
        imageDigest: `sha256:${"a".repeat(64)}`,
        commitSha: "a".repeat(40),
        pipelineUrl: "https://example.invalid/role",
        changeSummary: "Role request",
        requestedPolicyVersionId: null,
      },
    }]);
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "릴리스 요청이 거부되었습니다." })).toBeVisible();
    await expect(request).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

test("release request timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "DEVELOPER", { responseGate, responseGateOnce: true });
  try {
    await fillRequest(page);
    await page.evaluate(() => {
      const nativeFetch = window.fetch.bind(window);
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/releases" || init?.method !== "POST") return pending;
        return new Promise<Response>((resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true });
          pending.then(resolve, reject);
        });
      }) as typeof window.fetch;
    });
    const request = page.getByRole("button", { name: "릴리스 요청", exact: true });
    await request.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(page.getByRole("button", { name: "요청 중…", exact: true })).toBeDisabled();

    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible({ timeout: 20_000 });
    await expect(request).toBeEnabled();

    await request.click();
    await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

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
      await expect(page.getByRole("status").filter({ hasText: role === "APPROVER" ? "승인 결정이 기록되었습니다." : "promote 요청이 접수되었습니다." })).toBeVisible();
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

test("operator release actions remain single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, failure: "FORBIDDEN" });
  try {
    await load(page);
    const promote = page.getByRole("button", { name: "Promote", exact: true });
    const pause = page.getByRole("button", { name: "Pause", exact: true });
    await expect(promote).toBeEnabled();
    await expect(pause).toBeEnabled();
    page.on("dialog", (dialog) => dialog.accept("Concurrent operation fixture"));

    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll("button"));
      const promoteButton = buttons.find((button) => button.textContent === "Promote");
      const pauseButton = buttons.find((button) => button.textContent === "Pause");
      if (!promoteButton || !pauseButton) throw new Error("Release operation controls are unavailable");
      promoteButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      promoteButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      pauseButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations).toEqual([{
      path: "/releases/role-release/promote",
      body: { reason: "Concurrent operation fixture" },
    }]);
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "promote 요청이 거부되었습니다." })).toBeVisible();
    await expect(promote).toBeEnabled();
    await expect(pause).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

test("operator release action timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, responseGateOnce: true });
  try {
    await load(page);
    await page.evaluate(() => {
      const nativeFetch = window.fetch.bind(window);
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/releases/role-release/promote" || init?.method !== "POST") return pending;
        return new Promise<Response>((resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true });
          pending.then(resolve, reject);
        });
      }) as typeof window.fetch;
    });
    const promote = page.getByRole("button", { name: "Promote", exact: true });
    const pause = page.getByRole("button", { name: "Pause", exact: true });
    page.on("dialog", (dialog) => dialog.accept("Timeout recovery fixture"));

    await promote.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(promote).toBeDisabled();
    await expect(pause).toBeDisabled();

    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible({ timeout: 20_000 });
    await expect(promote).toBeEnabled();
    await expect(pause).toBeEnabled();

    await promote.click();
    await expect(page.getByRole("status").filter({ hasText: "promote 요청이 접수되었습니다." })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

test("approval decisions remain single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "APPROVER", { responseGate, failure: "FORBIDDEN" });
  try {
    await load(page);
    const approve = page.getByRole("button", { name: "Approve", exact: true });
    const reject = page.getByRole("button", { name: "Reject", exact: true });
    await expect(approve).toBeEnabled();
    await expect(reject).toBeEnabled();
    page.on("dialog", (dialog) => dialog.accept("Concurrent decision fixture"));

    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll("button"));
      const approveButton = buttons.find((button) => button.textContent === "Approve");
      const rejectButton = buttons.find((button) => button.textContent === "Reject");
      if (!approveButton || !rejectButton) throw new Error("Approval controls are unavailable");
      approveButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      approveButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      rejectButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations).toEqual([{
      path: "/releases/role-release/approve",
      body: { reason: "Concurrent decision fixture" },
    }]);
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "approve 요청이 거부되었습니다." })).toBeVisible();
    await expect(approve).toBeEnabled();
    await expect(reject).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

test("approval decision timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "APPROVER", { responseGate, responseGateOnce: true });
  try {
    await load(page);
    await page.evaluate(() => {
      const nativeFetch = window.fetch.bind(window);
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/releases/role-release/approve" || init?.method !== "POST") return pending;
        return new Promise<Response>((resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true });
          pending.then(resolve, reject);
        });
      }) as typeof window.fetch;
    });
    const approve = page.getByRole("button", { name: "Approve", exact: true });
    const reject = page.getByRole("button", { name: "Reject", exact: true });
    page.on("dialog", (dialog) => dialog.accept("Timeout recovery fixture"));

    await approve.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(approve).toBeDisabled();
    await expect(reject).toBeDisabled();

    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible({ timeout: 20_000 });
    await expect(approve).toBeEnabled();
    await expect(reject).toBeEnabled();

    await approve.click();
    await expect(page.getByRole("status").filter({ hasText: "승인 결정이 기록되었습니다." })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

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
    await expect(page.getByRole("status").filter({ hasText: notice })).toBeVisible();
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
    await expect(page.getByRole("status").filter({ hasText: role === "APPROVER" ? "승인 결정이 기록되었습니다." : "abort 요청이 접수되었습니다." })).toBeVisible();
    expect(state.mutations).toEqual([{ path: `/releases/role-release/${action}`, body: { reason } }]);
    expect(state.unexpected).toEqual([]);
  });
}

async function fixture(page: Page, role: string, options: { stale?: boolean; failure?: string; responseGate?: Promise<void>; responseGateOnce?: boolean; releaseLoadGate?: Promise<void>; releaseRefreshGate?: Promise<void>; auditIntegrityGate?: Promise<void>; connectionRefreshGate?: Promise<void>; connectionValidationGate?: Promise<void>; connectionCreateGate?: Promise<void>; connectionMutationGate?: Promise<void>; connectionAuditGate?: Promise<void>; disconnect?: boolean; disconnectOnce?: boolean; csrfFailure?: boolean; csrfStatus?: number; csrfRejectOnce?: boolean; csrfBody?: unknown; connections?: boolean; disabledPrometheus?: boolean; connectionAuditFailure?: boolean; connectionRefreshFailure?: boolean; releaseRefreshFailure?: boolean; releaseLoadFailure?: boolean; auditIntegrityFailure?: boolean; activeSessions?: boolean } = {}) {
  let status = role === "OPERATOR" ? "ANALYZING" : "PENDING_APPROVAL";
  const mutations: Mutation[] = [];
  const unexpected: string[] = [];
  const audit: object[] = [];
  let csrfRequests = 0;
  let clusterListRequests = 0;
  let prometheusListRequests = 0;
  let releaseListRequests = 0;
  let releaseLoadRequests = 0;
  let auditIntegrityRequests = 0;
  let connectionAuditRequests = 0;
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== origin) { unexpected.push(`External request: ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/control-api/")) return route.continue();
    const path = url.pathname.slice("/control-api".length);
    const json = (body: unknown, code = 200) => route.fulfill({ status: code, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() !== "GET") {
      const sessionMutation = role === "OPERATOR" && path === "/session/revoke-others";
      const individualSessionMutation = role === "OPERATOR" && request.method() === "DELETE" && path === "/session/active/other-session";
      const environmentValidationMutation = role === "OPERATOR" && path === `/environments/${environmentId}/validate`;
      const connectionValidationMutation = role === "OPERATOR" && request.method() === "POST" && /^\/connections\/(clusters|prometheus)(\/[^/]+)?\/validate$/.test(path);
      const connectionCreateMutation = role === "OPERATOR" && request.method() === "POST" && /^\/connections\/(clusters|prometheus)$/.test(path);
      const connectionMutation = role === "OPERATOR" && ((request.method() === "PUT" && /^\/connections\/(clusters|prometheus)\/[^/]+$/.test(path)) || (request.method() === "POST" && /^\/connections\/(clusters|prometheus)\/[^/]+\/(enable|disable)$/.test(path)));
      const permitted = (request.method() === "POST" && (
        (["DEVELOPER", "APPROVER", "OPERATOR"].includes(role) && path === "/releases") ||
        (role === "APPROVER" && /^\/releases\/role-release\/(approve|reject)$/.test(path)) ||
        (role === "OPERATOR" && (/^\/releases\/role-release\/(promote|pause|resume|abort)$/.test(path) || sessionMutation || environmentValidationMutation || connectionValidationMutation || connectionCreateMutation || connectionMutation))
      )) || individualSessionMutation || connectionMutation;
      if (!permitted || request.headers()["x-csrf-token"] !== "role-fixture-csrf" ||
          (!sessionMutation && !individualSessionMutation && !environmentValidationMutation && !connectionValidationMutation && !connectionCreateMutation && !connectionMutation && (!request.headers()["idempotency-key"] || !request.headers()["content-type"]?.includes("application/json")))) {
        unexpected.push(`Unsafe mutation: ${request.method()} ${path}`);
        return json({ code: "FORBIDDEN" }, 403);
      }
      mutations.push({ path, body: request.postData() ? request.postDataJSON() : null });
      if (options.responseGate && (!options.responseGateOnce || mutations.length === 1)) await options.responseGate;
      if (connectionValidationMutation && options.connectionValidationGate) await options.connectionValidationGate;
      if (connectionCreateMutation && options.connectionCreateGate) await options.connectionCreateGate;
      if (connectionMutation && options.connectionMutationGate) await options.connectionMutationGate;
      if (options.disconnect || (options.disconnectOnce && mutations.length === 1)) return route.abort("connectionfailed");
      if (options.failure) return json({ code: options.failure }, 403);
      if (connectionValidationMutation) return json(path.split("/").length === 4 ? [{ status: "ACTIVE" }] : { status: "ACTIVE" });
      if (connectionCreateMutation) return json({ id: path.endsWith("clusters") ? "cluster-created" : "prometheus-created", status: "PENDING_VALIDATION" }, 201);
      if (connectionMutation) return json({ status: path.endsWith("disable") ? "DISABLED" : "PENDING_VALIDATION" });
      if (environmentValidationMutation) return json({
        environmentId,
        status: "ACTIVE",
        checkedAt: new Date().toISOString(),
        validUntil: "2099-01-01T00:00:00Z",
        checks: [{ code: "FIXTURE_READY", outcome: "PASS", message: "Isolated readiness" }],
      });
      status = path.endsWith("/approve") ? "APPROVED" : path.endsWith("/reject") ? "REJECTED" : status;
      const action = path.split("/").at(-1)!;
      const eventType = path === "/releases" ? "RELEASE_REQUESTED" : ["approve", "reject"].includes(action)
        ? `RELEASE_${action === "approve" ? "APPROVED" : "REJECTED"}` : `ROLLOUT_${action.toUpperCase()}_REQUESTED`;
      audit.push({ id: "role-audit", eventType,
        actorType: "USER", actorId: "role-user", occurredAt: new Date().toISOString(), correlationId: "role-correlation", chainSequence: 1 });
      return json({ id: "role-release", status }, path === "/releases" ? 201 : 200);
    }
    if (path === "/session/providers") return json({ oidc: false, loginUrl: null });
    if (path === "/session") return json({ user: { id: "role-user", displayName: "Role fixture", username: "role-user", email: `${role.toLowerCase()}@example.test`, roles: [role], demo: false }, csrfToken: "role-fixture-csrf", expiresAt: "2099-01-01T00:00:00Z" });
    if (path === "/session/csrf") {
      if ("csrfBody" in options) return json(options.csrfBody);
      csrfRequests++;
      return options.csrfFailure ? route.abort("connectionfailed") : json({ headerName: "X-CSRF-TOKEN", token: "role-fixture-csrf" }, options.csrfRejectOnce && csrfRequests === 1 ? 403 : options.csrfStatus || 200);
    }
    if (path === "/session/active") return json({ items: options.activeSessions ? [
      { reference: "current-session", current: true, createdAt: "2026-09-17T00:00:00Z", lastAccessedAt: "2026-09-17T00:01:00Z", expiresAt: "2099-01-01T00:00:00Z" },
      { reference: "other-session", current: false, createdAt: "2026-09-17T00:00:00Z", lastAccessedAt: "2026-09-17T00:01:00Z", expiresAt: "2099-01-01T00:00:00Z" },
    ] : [] });
    if (path === "/audit-events/verify") {
      auditIntegrityRequests++;
      if (options.auditIntegrityGate && auditIntegrityRequests > 1) await options.auditIntegrityGate;
      return options.auditIntegrityFailure
        ? json({ code: "AUDIT_VERIFICATION_UNAVAILABLE" }, 503)
        : json({ valid: true, verifiedEvents: audit.length, failedEventId: null, headHash: "a".repeat(64) });
    }
    if (path === "/audit-events") {
      const aggregateType = url.searchParams.get("aggregateType");
      if (["CLUSTER_CONNECTION", "PROMETHEUS_CONNECTION"].includes(aggregateType ?? "")) {
        connectionAuditRequests++;
        if (options.connectionAuditGate) await options.connectionAuditGate;
      }
      if (options.connectionAuditFailure && ["CLUSTER_CONNECTION", "PROMETHEUS_CONNECTION"].includes(aggregateType ?? "")) return json({ code: "AUDIT_UNAVAILABLE" }, 503);
      return json({ items: audit });
    }
    if (path === "/connections/clusters") {
      clusterListRequests++;
      if (options.connectionRefreshGate && clusterListRequests > 1) await options.connectionRefreshGate;
      if (options.connectionRefreshFailure && clusterListRequests > 1) return json({ code: "CONNECTIONS_UNAVAILABLE" }, 503);
      return json(options.connections ? [{ id: "cluster-role", name: "Role cluster", apiServer: "https://cluster.example", allowedNamespaces: ["releasepilot"], secretRef: "env:KUBERNETES_TOKEN", status: "ACTIVE", lastValidatedAt: new Date().toISOString() }] : []);
    }
    if (path === "/connections/prometheus") {
      prometheusListRequests++;
      if (options.connectionRefreshGate && prometheusListRequests > 1) await options.connectionRefreshGate;
      if (options.connectionRefreshFailure && prometheusListRequests > 1) return json({ code: "CONNECTIONS_UNAVAILABLE" }, 503);
      return json(options.connections ? [{ id: "prometheus-role", name: "Role metrics", baseUrl: "https://metrics.example", secretRef: null, status: options.disabledPrometheus ? "DISABLED" : "ACTIVE", lastValidatedAt: new Date().toISOString(), queryTimeoutSeconds: 15 }] : []);
    }
    if (path === "/projects") return json({ items: [{ id: "project-role", name: "Role project", key: "role", status: "ACTIVE" }] });
    if (path === "/projects/project-role/services") return json({ items: [{ id: serviceId, name: "Role service", key: "role", status: "ACTIVE" }] });
    if (path === `/services/${serviceId}/environments`) return json({ items: [{ id: environmentId, name: "Role environment", status: "ACTIVE", strategy: "CANARY" }] });
    if (path === `/environments/${environmentId}/validation-results/latest`) return json({ environmentId, status: "ACTIVE", checkedAt: new Date().toISOString(), validUntil: options.stale ? "2020-01-01T00:00:00Z" : "2099-01-01T00:00:00Z", checks: [{ code: "FIXTURE_READY", outcome: "PASS", message: "Isolated readiness" }] });
    if (path === "/releases") {
      releaseListRequests++;
      if (options.releaseRefreshGate && releaseListRequests > 1) await options.releaseRefreshGate;
      if (options.releaseRefreshFailure && releaseListRequests > 1) return json({ code: "RELEASES_UNAVAILABLE" }, 503);
      return json({ items: [{ id: "role-release", version: "v-role", status, createdAt: new Date().toISOString(), context: { serviceName: "Role service", environmentName: "Role environment" } }] });
    }
    if (path === "/releases/role-release/analyses") return json([]);
    if (path === "/releases/role-release/events") return route.fulfill({ contentType: "text/event-stream", body: `event: release-state\ndata: ${JSON.stringify({ releaseStatus: status, steps: [] })}\n\n` });
    if (path === "/releases/role-release") {
      releaseLoadRequests++;
      if (options.releaseLoadGate) await options.releaseLoadGate;
      return options.releaseLoadFailure
        ? json({ code: "RELEASE_UNAVAILABLE" }, 503)
        : json({ id: "role-release", environmentId, version: "v-role", status, createdAt: new Date().toISOString(), imageRepository: "example.invalid/role", imageDigest: `sha256:${"a".repeat(64)}`, changeSummary: "Role fixture", commitSha: "a".repeat(40), pipelineUrl: "https://example.invalid/role", context: { serviceName: "Role service", environmentName: "Role environment" }, requester: { displayName: "Other requester", username: "other" }, policySnapshot: { name: "Role policy", checksum: "role-policy", definition: { strategy: "CANARY", steps: [], metrics: [] } } });
    }
    unexpected.push(`Unhandled API: ${path}`);
    return json({ code: "UNHANDLED_FIXTURE" }, 500);
  });
  return { mutations, unexpected, clusterListRequests: () => clusterListRequests, prometheusListRequests: () => prometheusListRequests, releaseListRequests: () => releaseListRequests, releaseLoadRequests: () => releaseLoadRequests, auditIntegrityRequests: () => auditIntegrityRequests, connectionAuditRequests: () => connectionAuditRequests };
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
    await expect(page.getByRole("status").filter({ hasText: action === "approve" ? "승인 결정이 기록되었습니다." : "거부 결정이 기록되었습니다." })).toBeVisible();
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
    await expect(page.getByRole("status").filter({ hasText: `${action} 요청이 접수되었습니다.` })).toBeVisible();
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

for (const role of ["OPERATOR", "DEVELOPER", "APPROVER"] as const) {
  test(`shows the signed-in email and ${role} role`, async ({ page }) => {
    await fixture(page, role);
    await page.goto("/");
    const identity = page.getByLabel("현재 로그인 계정");
    await expect(identity).toContainText(`${role.toLowerCase()}@example.test`);
    await expect(identity).toContainText(role);
  });
}

test("account switch ends the current session before opening SSO sign-in", async ({ page }) => {
  await fixture(page, "APPROVER");
  let loggedOut = false;
  await page.route("**/control-api/session/providers", route => route.fulfill({ contentType: "application/json", body: JSON.stringify({ oidc: true, loginUrl: "/oauth2/authorization/releasepilot" }) }));
  await page.route("**/control-api/session/logout", route => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-csrf-token"]).toBe("role-fixture-csrf");
    loggedOut = true;
    return route.fulfill({ status: 204 });
  });
  await page.route("**/oauth2/authorization/releasepilot", route => {
    expect(loggedOut).toBe(true);
    return route.fulfill({ contentType: "text/html", body: "<meta charset=\"utf-8\"><h1>다른 계정으로 로그인</h1>" });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "계정 변경", exact: true }).click();
  await expect(page.getByRole("heading", { name: "다른 계정으로 로그인" })).toBeVisible();
});

test("logout and account switch remain single-flight before busy state renders", async ({ page }) => {
  await fixture(page, "APPROVER");
  let respond!: () => void;
  const gate = new Promise<void>((resolve) => { respond = resolve; });
  let logoutRequests = 0;
  let loginRequests = 0;
  await page.route("**/control-api/session/providers", route => route.fulfill({ contentType: "application/json", body: JSON.stringify({ oidc: true, loginUrl: "/oauth2/authorization/releasepilot" }) }));
  await page.route("**/control-api/session/logout", async (route) => {
    logoutRequests++;
    await gate;
    return route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ code: "FORBIDDEN" }) });
  });
  await page.route("**/oauth2/authorization/releasepilot", route => {
    loginRequests++;
    return route.fulfill({ contentType: "text/html", body: "<meta charset=\"utf-8\"><h1>다른 계정으로 로그인</h1>" });
  });
  try {
    await page.goto("/");
    await expect(page.getByRole("button", { name: "로그아웃", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "계정 변경", exact: true })).toBeVisible();
    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll("button"));
      const logout = buttons.find((button) => button.textContent === "로그아웃");
      const changeAccount = buttons.find((button) => button.textContent === "계정 변경");
      if (!logout || !changeAccount) throw new Error("Session controls are unavailable");
      logout.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      logout.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      changeAccount.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await expect.poll(() => logoutRequests).toBe(1);
    expect(loginRequests).toBe(0);
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "로그아웃하지 못했습니다. 다시 시도해 주세요." })).toBeVisible();
    await expect(page.getByRole("button", { name: "로그아웃", exact: true })).toBeEnabled();
    expect(logoutRequests).toBe(1);
    expect(loginRequests).toBe(0);
  } finally { respond(); }
});

test("logout timeout restores both session controls and permits one clean retry", async ({ page }) => {
  await fixture(page, "APPROVER");
  let respond!: () => void;
  const gate = new Promise<void>((resolve) => { respond = resolve; });
  let logoutRequests = 0;
  await page.route("**/control-api/session/providers", route => route.fulfill({ contentType: "application/json", body: JSON.stringify({ oidc: true, loginUrl: "/oauth2/authorization/releasepilot" }) }));
  await page.route("**/control-api/session/logout", async (route) => {
    logoutRequests++;
    if (logoutRequests === 1) await gate;
    return route.fulfill({ status: 204 });
  });
  try {
    await page.goto("/");
    await page.evaluate(() => {
      const nativeFetch = window.fetch.bind(window);
      let attempts = 0;
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/session/logout" || init?.method !== "POST") return pending;
        attempts++;
        if (attempts > 1) return pending;
        void pending.catch(() => undefined);
        return new Promise<Response>((_resolve, reject) => {
          setTimeout(() => reject(new Error("요청 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도하세요.")), 1_000);
        });
      }) as typeof window.fetch;
    });
    const logout = page.getByRole("button", { name: "로그아웃", exact: true });
    const changeAccount = page.getByRole("button", { name: "계정 변경", exact: true });

    await expect(changeAccount).toBeVisible();
    await logout.click();
    await expect(page.getByRole("button", { name: "로그아웃 중…", exact: true })).toBeDisabled();
    await expect.poll(() => logoutRequests).toBe(1);
    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible();
    await expect(logout).toBeEnabled();
    await expect(changeAccount).toBeEnabled();
    await expect(logout).toBeFocused();

    respond();
    await logout.click();
    await expect.poll(() => logoutRequests).toBe(2);
  } finally { respond(); }
});

for (const role of ["DEVELOPER", "APPROVER", "OPERATOR"] as const) {
  test(`@a11y ${role} screen meets automated WCAG A and AA checks`, async ({ page }) => {
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    expect(results.violations.map(({ id, nodes }) => ({ id, targets: nodes.map((node) => node.target) }))).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} primary action is reachable with visible keyboard focus`, async ({ page }) => {
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const action = role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Promote";
    await page.locator("body").focus();
    let reached = false;
    for (let index = 0; index < 80; index++) {
      await page.keyboard.press("Tab");
      const active = await page.evaluate(() => ({
        name: (document.activeElement?.getAttribute("aria-label") || document.activeElement?.textContent || "").trim(),
        tag: document.activeElement?.tagName || "",
        outline: getComputedStyle(document.activeElement as Element).outlineStyle,
      }));
      if (active.tag === "BUTTON" && active.name === action) {
        expect(active.outline).not.toBe("none");
        reached = true;
        break;
      }
    }
    expect(reached).toBe(true);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} high contrast mode reflows without clipping controls`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    await page.emulateMedia({ forcedColors: "active" });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const actionName = role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Promote";
    const action = page.getByRole("button", { name: actionName, exact: true });
    await expect(action).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await action.scrollIntoViewIfNeeded();
    const box = await action.boundingBox();
    expect(box).toBeTruthy();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(320);
    await page.locator("body").focus();
    let focusVisible = false;
    for (let index = 0; index < 100; index++) {
      await page.keyboard.press("Tab");
      focusVisible = await action.evaluate((element) => element === document.activeElement && element.matches(":focus-visible"));
      if (focusVisible) break;
    }
    expect(focusVisible).toBe(true);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} supports WCAG text spacing at 320px`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    await page.addStyleTag({ content: `
      * { line-height: 1.5 !important; letter-spacing: .12em !important; word-spacing: .16em !important; }
      p { margin-bottom: 2em !important; }
    ` });
    const actionName = role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Promote";
    const action = page.getByRole("button", { name: actionName, exact: true });
    await expect(action).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await action.scrollIntoViewIfNeeded();
    const box = await action.boundingBox();
    expect(box).toBeTruthy();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(320);
    expect(await action.evaluate((element) => element.scrollWidth <= element.clientWidth + 1 && element.scrollHeight <= element.clientHeight + 1)).toBe(true);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} honors reduced motion preferences`, async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
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
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} pointer targets meet the 24px minimum at 320px`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
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
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} keyboard focus is not obscured at 320px`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const result = await inspectTabFocusVisibility(page, 120);
    expect(result.visited).toBeGreaterThan(0);
    expect(result.obscured).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} keyboard focus indicator meets WCAG 2.4.13 minimum`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const result = await inspectTabFocusAppearance(page, 120);
    expect(result.visited).toBeGreaterThan(0);
    expect(result.failures).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} can bypass repeated navigation with the skip link`, async ({ page }) => {
    const state = await fixture(page, role);
    await page.goto("/");
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
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} exposes a titled landmark hierarchy`, async ({ page }) => {
    const state = await fixture(page, role);
    await page.goto("/");
    await expect(page).toHaveTitle("ReleasePilot — Safe delivery control plane");
    await expect(page.getByRole("main")).toHaveAccessibleName("Progressive delivery control room");
    await expect(page.getByRole("navigation", { name: "주요 탐색 및 계정 제어" })).toBeVisible();
    await expect(page.getByRole("heading", { level: 1, name: "Progressive delivery control room" })).toHaveCount(1);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} announces session connection status without moving focus`, async ({ page }) => {
    const state = await fixture(page, role);
    await page.goto("/");
    const status = page.locator("span[role=status][aria-live=polite][aria-atomic=true]");
    await expect(status).toHaveText("SIGNED IN");
    await expect(status.locator("i")).toHaveAttribute("aria-hidden", "true");
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} status messages use a consistent polite atomic contract`, async ({ page }) => {
    const state = await fixture(page, role);
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const statuses = page.locator('[role="status"]');
    expect(await statuses.count()).toBeGreaterThan(0);
    expect(await statuses.evaluateAll((elements) => elements.filter((element) => element.getAttribute("aria-live") !== "polite" || element.getAttribute("aria-atomic") !== "true").map((element) => element.textContent?.trim()))).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} error messages use a consistent assertive atomic contract`, async ({ page }) => {
    const state = await fixture(page, role, { csrfBody: null });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const button = page.getByRole("button", { name: role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort", exact: true });
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Accessibility error fixture"));
    await button.click();
    await expect(page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." })).toBeVisible();
    const alerts = page.locator('[role="alert"]').filter({ hasText: /\S/ });
    expect(await alerts.count()).toBeGreaterThan(0);
    expect(await alerts.evaluateAll((elements) => elements.filter((element) => element.getAttribute("aria-live") !== "assertive" || element.getAttribute("aria-atomic") !== "true").map((element) => element.textContent?.trim()))).toEqual([]);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} error announcements preserve the triggering control focus`, async ({ page }) => {
    const state = await fixture(page, role, { csrfBody: null });
    if (role === "DEVELOPER") await fillRequest(page);
    else await load(page);
    const button = page.getByRole("button", { name: role === "DEVELOPER" ? "릴리스 요청" : role === "APPROVER" ? "Approve" : "Abort", exact: true });
    if (role !== "DEVELOPER") page.once("dialog", (dialog) => dialog.accept("Accessibility focus fixture"));
    await button.focus();
    await button.press("Enter");
    await expect(page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." })).toBeVisible();
    await expect(button).toBeFocused();
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });

  test(`@a11y ${role} release validation identifies and focuses the invalid field`, async ({ page }) => {
    const state = await fixture(page, role);
    await fillRequest(page);
    const policyVersion = page.getByLabel("Policy Version ID", { exact: false });
    await policyVersion.fill("invalid-policy-version");
    await page.getByRole("button", { name: "릴리스 요청", exact: true }).click();
    const alert = page.getByRole("alert").filter({ hasText: "Policy Version ID는 올바른 UUID여야 합니다." });
    await expect(alert).toHaveAttribute("id", "release-request-error");
    await expect(policyVersion).toHaveAttribute("aria-invalid", "true");
    await expect(policyVersion).toHaveAttribute("aria-errormessage", "release-request-error");
    await expect(policyVersion).toBeFocused();
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  });
}

test("@a11y OPERATOR connection validation identifies and focuses invalid fields", async ({ page }) => {
  const state = await fixture(page, "OPERATOR");
  await page.goto("/");
  await page.getByText("NEW CONNECTION", { exact: false }).click();

  const clusterForm = page.locator("form").filter({ has: page.getByText("Kubernetes cluster", { exact: true }) });
  await clusterForm.getByLabel("Name", { exact: true }).fill("Production cluster");
  await clusterForm.getByLabel("API server", { exact: true }).fill("https://cluster.example");
  await clusterForm.getByLabel("Allowed namespaces", { exact: true }).fill("releasepilot");
  const clusterSecret = clusterForm.getByLabel("Secret reference", { exact: true });
  await clusterSecret.fill("invalid secret reference");
  await clusterForm.getByRole("button", { name: "Cluster 등록", exact: true }).click();
  let alert = page.getByRole("alert").filter({ hasText: "Secret reference 형식을 확인하세요." });
  await expect(alert).toHaveAttribute("id", "connection-form-error");
  await expect(clusterSecret).toHaveAttribute("aria-invalid", "true");
  await expect(clusterSecret).toHaveAttribute("aria-errormessage", "connection-form-error");
  await expect(clusterSecret).toBeFocused();
  await clusterSecret.fill("env:KUBERNETES_TOKEN");
  await expect(alert).toHaveCount(0);

  const prometheusForm = page.locator("form").filter({ has: page.getByText("Prometheus", { exact: true }) });
  await prometheusForm.getByLabel("Name", { exact: true }).fill("Production metrics");
  await prometheusForm.getByLabel("Base URL", { exact: true }).fill("https://metrics.example");
  const prometheusSecret = prometheusForm.getByLabel("Secret reference", { exact: false });
  await prometheusSecret.fill("invalid secret reference");
  await prometheusForm.getByRole("button", { name: "Prometheus 등록", exact: true }).click();
  alert = page.getByRole("alert").filter({ hasText: "Secret reference 형식을 확인하세요." });
  await expect(alert).toHaveAttribute("id", "connection-form-error");
  await expect(prometheusSecret).toHaveAttribute("aria-invalid", "true");
  await expect(prometheusSecret).toHaveAttribute("aria-errormessage", "connection-form-error");
  await expect(prometheusSecret).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR connection registration errors preserve submit focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { csrfBody: null });
  await page.goto("/");
  await page.getByText("NEW CONNECTION", { exact: false }).click();

  const clusterForm = page.locator("form").filter({ has: page.getByText("Kubernetes cluster", { exact: true }) });
  await clusterForm.getByLabel("Name", { exact: true }).fill("Production cluster");
  await clusterForm.getByLabel("API server", { exact: true }).fill("https://cluster.example");
  await clusterForm.getByLabel("Allowed namespaces", { exact: true }).fill("releasepilot");
  await clusterForm.getByLabel("Secret reference", { exact: true }).fill("env:KUBERNETES_TOKEN");
  const clusterSubmit = clusterForm.getByRole("button", { name: "Cluster 등록", exact: true });
  await clusterSubmit.focus();
  await clusterSubmit.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterSubmit).toBeFocused();

  const prometheusForm = page.locator("form").filter({ has: page.getByText("Prometheus", { exact: true }) });
  await prometheusForm.getByLabel("Name", { exact: true }).fill("Production metrics");
  await prometheusForm.getByLabel("Base URL", { exact: true }).fill("https://metrics.example");
  const prometheusSubmit = prometheusForm.getByRole("button", { name: "Prometheus 등록", exact: true });
  await prometheusSubmit.focus();
  await prometheusSubmit.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusSubmit).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("OPERATOR connection registration remains single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const connectionCreateGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connectionCreateGate });
  try {
    await page.goto("/");
    await page.getByText("NEW CONNECTION", { exact: false }).click();
    const clusterForm = page.locator("form").filter({ has: page.getByText("Kubernetes cluster", { exact: true }) });
    await clusterForm.getByLabel("Name", { exact: true }).fill("Production cluster");
    await clusterForm.getByLabel("API server", { exact: true }).fill("https://cluster.example");
    await clusterForm.getByLabel("Allowed namespaces", { exact: true }).fill("releasepilot");
    await clusterForm.getByLabel("Secret reference", { exact: true }).fill("env:KUBERNETES_TOKEN");
    const prometheusForm = page.locator("form").filter({ has: page.getByText("Prometheus", { exact: true }) });
    await prometheusForm.getByLabel("Name", { exact: true }).fill("Production metrics");
    await prometheusForm.getByLabel("Base URL", { exact: true }).fill("https://metrics.example");
    await clusterForm.evaluate((form) => {
      form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    await prometheusForm.evaluate((form) => form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })));
    expect(state.mutations).toEqual([{ path: "/connections/clusters", body: { name: "Production cluster", apiServer: "https://cluster.example", allowedNamespaces: ["releasepilot"], secretRef: "env:KUBERNETES_TOKEN" } }]);
    respond();
    await expect(clusterForm.getByRole("button", { name: "Cluster 등록", exact: true })).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("connection registration timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, responseGateOnce: true });
  try {
    await page.addInitScript(() => {
      const nativeFetch = window.fetch.bind(window);
      let attempts = 0;
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/connections/clusters" || init?.method !== "POST") return pending;
        attempts++;
        if (attempts > 1) return pending;
        void pending.catch(() => undefined);
        return new Promise<Response>((_resolve, reject) => {
          setTimeout(() => reject(new Error("요청 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도하세요.")), 1_000);
        });
      }) as typeof window.fetch;
    });
    await page.goto("/");
    await page.getByText("NEW CONNECTION", { exact: false }).click();
    const clusterForm = page.locator("form").filter({ has: page.getByText("Kubernetes cluster", { exact: true }) });
    await clusterForm.getByLabel("Name", { exact: true }).fill("Production cluster");
    await clusterForm.getByLabel("API server", { exact: true }).fill("https://cluster.example");
    await clusterForm.getByLabel("Allowed namespaces", { exact: true }).fill("releasepilot");
    await clusterForm.getByLabel("Secret reference", { exact: true }).fill("env:KUBERNETES_TOKEN");
    const submit = clusterForm.getByRole("button", { name: "Cluster 등록", exact: true });

    await submit.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(clusterForm.getByRole("button", { name: "등록 중…", exact: true })).toBeDisabled();
    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible();
    await expect(submit).toBeEnabled();

    respond();
    await submit.click();
    await expect(page.getByText("Kubernetes 연결을 등록했습니다. 사용 전에 연결 검증을 실행하세요.", { exact: true })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("OPERATOR connection changes remain single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const connectionMutationGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, disabledPrometheus: true, connectionMutationGate });
  try {
    await page.goto("/");
    const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
    const prometheusEnable = prometheus.getByRole("button", { name: "재활성화", exact: true });
    await prometheusEnable.evaluate((button) => {
      (button as HTMLButtonElement).click();
      (button as HTMLButtonElement).click();
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    const cluster = page.locator("article").filter({ hasText: "Role cluster" });
    await cluster.getByRole("button", { name: "편집", exact: true }).evaluate((button) => (button as HTMLButtonElement).click());
    expect(state.mutations).toEqual([{ path: "/connections/prometheus/prometheus-role/enable", body: null }]);
    respond();
    await expect(prometheusEnable).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("connection status timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, disabledPrometheus: true, responseGate, responseGateOnce: true });
  try {
    await page.addInitScript(() => {
      const nativeFetch = window.fetch.bind(window);
      let attempts = 0;
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/connections/prometheus/prometheus-role/enable" || init?.method !== "POST") return pending;
        attempts++;
        if (attempts > 1) return pending;
        void pending.catch(() => undefined);
        return new Promise<Response>((_resolve, reject) => {
          setTimeout(() => reject(new Error("요청 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도하세요.")), 1_000);
        });
      }) as typeof window.fetch;
    });
    await page.goto("/");
    const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
    const enable = prometheus.getByRole("button", { name: "재활성화", exact: true });

    await enable.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(enable).toBeDisabled();
    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible();
    await expect(enable).toBeEnabled();

    respond();
    await enable.click();
    await expect(page.getByText("연결을 재활성화했습니다. 사용 전에 연결 검증을 실행하세요.", { exact: true })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR connection validation errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { csrfBody: null, connections: true });
  await page.goto("/");

  const cluster = page.locator("article").filter({ hasText: "Role cluster" });
  const clusterValidate = cluster.getByRole("button", { name: "연결 검증", exact: true });
  await clusterValidate.focus();
  await clusterValidate.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterValidate).toBeFocused();

  const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
  const prometheusValidate = prometheus.getByRole("button", { name: "연결 검증", exact: true });
  await prometheusValidate.focus();
  await prometheusValidate.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusValidate).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR bulk connection validation errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { csrfBody: null, connections: true });
  await page.goto("/");

  const clusterValidateAll = page.getByRole("button", { name: "Kubernetes 전체 검증", exact: true });
  await clusterValidateAll.focus();
  await clusterValidateAll.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterValidateAll).toBeFocused();

  const prometheusValidateAll = page.getByRole("button", { name: "Prometheus 전체 검증", exact: true });
  await prometheusValidateAll.focus();
  await prometheusValidateAll.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusValidateAll).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR connection status errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { csrfBody: null, connections: true, disabledPrometheus: true });
  await page.goto("/");

  const cluster = page.locator("article").filter({ hasText: "Role cluster" });
  const clusterDisable = cluster.getByRole("button", { name: "비활성화", exact: true });
  page.once("dialog", (dialog) => dialog.accept());
  await clusterDisable.focus();
  await clusterDisable.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterDisable).toBeFocused();

  const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
  const prometheusEnable = prometheus.getByRole("button", { name: "재활성화", exact: true });
  await prometheusEnable.focus();
  await prometheusEnable.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusEnable).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR connection edit errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { csrfBody: null, connections: true });
  await page.goto("/");
  page.on("dialog", (dialog) => dialog.accept(dialog.defaultValue()));

  const cluster = page.locator("article").filter({ hasText: "Role cluster" });
  const clusterEdit = cluster.getByRole("button", { name: "편집", exact: true });
  await clusterEdit.focus();
  await clusterEdit.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterEdit).toBeFocused();

  const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
  const prometheusEdit = prometheus.getByRole("button", { name: "편집", exact: true });
  await prometheusEdit.focus();
  await prometheusEdit.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "보안 토큰 응답이 올바르지 않습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusEdit).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR connection audit errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { connections: true, connectionAuditFailure: true });
  await page.goto("/");

  const cluster = page.locator("article").filter({ hasText: "Role cluster" });
  const clusterAudit = cluster.getByRole("button", { name: "감사 이력", exact: true });
  await clusterAudit.focus();
  await clusterAudit.press("Enter");
  let alert = page.getByRole("alert").filter({ hasText: "연결 감사 이력을 불러올 수 없습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(clusterAudit).toBeFocused();

  const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
  const prometheusAudit = prometheus.getByRole("button", { name: "감사 이력", exact: true });
  await prometheusAudit.focus();
  await prometheusAudit.press("Enter");
  alert = page.getByRole("alert").filter({ hasText: "연결 감사 이력을 불러올 수 없습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(prometheusAudit).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("OPERATOR connection audit remains single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const connectionAuditGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, connectionAuditGate });
  try {
    await page.goto("/");
    const cluster = page.locator("article").filter({ hasText: "Role cluster" });
    const clusterAudit = cluster.getByRole("button", { name: "감사 이력", exact: true });
    await clusterAudit.evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await expect.poll(state.connectionAuditRequests).toBe(1);
    const prometheus = page.locator("article").filter({ hasText: "Role metrics" });
    await prometheus.getByRole("button", { name: "감사 이력", exact: true }).evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(state.connectionAuditRequests()).toBe(1);
    respond();
    await expect(cluster.getByRole("button", { name: "이력 닫기", exact: true })).toBeEnabled();
    expect(state.connectionAuditRequests()).toBe(1);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR connection refresh errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { connections: true, connectionRefreshFailure: true });
  await page.goto("/");

  const refresh = page.getByRole("button", { name: "새로고침", exact: true });
  await refresh.focus();
  await refresh.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: /연결 목록을 불러올 수 없습니다/ });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(refresh).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("connection refresh remains locked until both list requests complete", async ({ page }) => {
  let respond!: () => void;
  const connectionRefreshGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, connectionRefreshGate });
  try {
    await page.goto("/");
    await expect.poll(state.clusterListRequests).toBe(1);
    await expect.poll(state.prometheusListRequests).toBe(1);
    const refresh = page.getByRole("button", { name: "새로고침", exact: true });
    await refresh.click();
    await expect.poll(state.clusterListRequests).toBe(2);
    await expect.poll(state.prometheusListRequests).toBe(2);
    const pending = page.getByRole("button", { name: "새로고침 중…", exact: true });
    await expect(pending).toBeDisabled();
    await pending.evaluate((element) => (element as HTMLButtonElement).click());
    expect(state.clusterListRequests()).toBe(2);
    expect(state.prometheusListRequests()).toBe(2);
    respond();
    await expect(refresh).toBeEnabled();
    expect(state.clusterListRequests()).toBe(2);
    expect(state.prometheusListRequests()).toBe(2);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("OPERATOR connection validation remains single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const connectionValidationGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, connectionValidationGate });
  try {
    await page.goto("/");
    const cluster = page.locator("article").filter({ hasText: "Role cluster" });
    const validate = cluster.getByRole("button", { name: "연결 검증", exact: true });
    await validate.evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    await page.getByRole("button", { name: "Prometheus 전체 검증", exact: true }).evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(state.mutations).toEqual([{ path: "/connections/clusters/cluster-role/validate", body: null }]);
    respond();
    await expect(validate).toBeEnabled();
    expect(state.mutations).toEqual([{ path: "/connections/clusters/cluster-role/validate", body: null }]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("connection validation timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { connections: true, responseGate, responseGateOnce: true });
  try {
    await page.addInitScript(() => {
      const nativeFetch = window.fetch.bind(window);
      let validationAttempts = 0;
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (!url.pathname.includes("/connections/") || !url.pathname.endsWith("/validate") || init?.method !== "POST") return pending;
        validationAttempts++;
        if (validationAttempts > 1) return pending;
        void pending.catch(() => undefined);
        return new Promise<Response>((_resolve, reject) => {
          setTimeout(() => reject(new Error("요청 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도하세요.")), 1_000);
        });
      }) as typeof window.fetch;
    });
    await page.goto("/");
    const cluster = page.locator("article").filter({ hasText: "Role cluster" });
    const validate = cluster.getByRole("button", { name: "연결 검증", exact: true });

    await validate.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(cluster.getByRole("button", { name: "처리 중…", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Prometheus 전체 검증", exact: true })).toBeDisabled();

    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible();
    await expect(validate).toBeEnabled();

    respond();
    await validate.click();
    await expect(page.getByText("Kubernetes 연결 검증을 통과했습니다.", { exact: true })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR release refresh errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { releaseRefreshFailure: true });
  await page.goto("/");

  const refresh = page.getByRole("button", { name: "최근 릴리스 새로고침", exact: true });
  await refresh.focus();
  await refresh.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "최근 릴리스를 불러올 수 없습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(refresh).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("release refresh remains locked until the current list request completes", async ({ page }) => {
  let respond!: () => void;
  const releaseRefreshGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { releaseRefreshGate });
  try {
    await page.goto("/");
    await expect.poll(state.releaseListRequests).toBe(1);
    const refresh = page.getByRole("button", { name: "최근 릴리스 새로고침", exact: true });
    await refresh.click();
    await expect.poll(state.releaseListRequests).toBe(2);
    await expect(refresh).toBeDisabled();
    await refresh.evaluate((element) => (element as HTMLButtonElement).click());
    expect(state.releaseListRequests()).toBe(2);
    respond();
    await expect(refresh).toBeEnabled();
    expect(state.releaseListRequests()).toBe(2);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR release load errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { releaseLoadFailure: true });
  await page.goto("/");
  await page.getByRole("combobox", { name: "최근 릴리스" }).selectOption("role-release");

  const loadRelease = page.getByRole("button", { name: "불러오기", exact: true });
  await loadRelease.focus();
  await loadRelease.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "릴리스 조회 권한 또는 ID를 확인하세요." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(loadRelease).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("release load remains locked until the current detail request completes", async ({ page }) => {
  let respond!: () => void;
  const releaseLoadGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { releaseLoadGate });
  try {
    await page.goto("/");
    const loadRelease = page.getByRole("button", { name: "불러오기", exact: true });
    await loadRelease.click();
    await expect.poll(state.releaseLoadRequests).toBe(1);
    const pending = page.getByRole("button", { name: "조회 중…", exact: true });
    await expect(pending).toBeDisabled();
    await pending.evaluate((element) => (element as HTMLButtonElement).click());
    expect(state.releaseLoadRequests()).toBe(1);
    respond();
    await expect(page.getByRole("heading", { name: "release · v-role", exact: true })).toBeVisible();
    await expect(loadRelease).toBeEnabled();
    expect(state.releaseLoadRequests()).toBe(1);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR audit integrity errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { auditIntegrityFailure: true });
  await page.goto("/");

  const verify = page.getByRole("button", { name: "Verification unavailable", exact: true });
  await verify.focus();
  await verify.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "감사 체인을 검증할 수 없습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(verify).toBeFocused();
  expect(state.mutations).toEqual([]);
  expect(state.unexpected).toEqual([]);
});

test("audit integrity verification remains locked until the current request completes", async ({ page }) => {
  let respond!: () => void;
  const auditIntegrityGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { auditIntegrityGate });
  try {
    await page.goto("/");
    await expect.poll(state.releaseListRequests).toBe(1);
    await expect.poll(state.auditIntegrityRequests).toBe(1);
    const verify = page.getByRole("button", { name: "Verified · 0 events", exact: true });
    await verify.click();
    await expect.poll(state.auditIntegrityRequests).toBe(2);
    const pending = page.getByRole("button", { name: "Verifying…", exact: true });
    await expect(pending).toBeDisabled();
    await pending.evaluate((element) => (element as HTMLButtonElement).click());
    expect(state.auditIntegrityRequests()).toBe(2);
    respond();
    await expect(page.getByRole("button", { name: "Verified · 0 events", exact: true })).toBeEnabled();
    expect(state.auditIntegrityRequests()).toBe(2);
    expect(state.mutations).toEqual([]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR revoke other sessions errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { activeSessions: true, failure: "SESSION_REVOKE_REJECTED" });
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/");

  const revoke = page.getByRole("button", { name: "다른 세션 모두 종료", exact: true });
  await revoke.focus();
  await revoke.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "다른 세션 종료 요청이 거부되었습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(revoke).toBeFocused();
  expect(state.mutations).toEqual([{ path: "/session/revoke-others", body: null }]);
  expect(state.unexpected).toEqual([]);
});

test("@a11y OPERATOR individual session revoke errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { activeSessions: true, failure: "SESSION_REVOKE_REJECTED" });
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/");

  const revoke = page.getByRole("button", { name: "종료", exact: true });
  await revoke.focus();
  await revoke.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "세션 종료 요청이 거부되었습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(revoke).toBeFocused();
  expect(state.mutations).toEqual([{ path: "/session/active/other-session", body: null }]);
  expect(state.unexpected).toEqual([]);
});

test("OPERATOR session revocation remains single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { activeSessions: true, responseGate });
  page.on("dialog", (dialog) => dialog.accept());
  try {
    await page.goto("/");
    const revoke = page.getByRole("button", { name: "종료", exact: true });
    await revoke.evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    await page.getByRole("button", { name: "다른 세션 모두 종료", exact: true }).evaluate((element) => {
      element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(state.mutations).toEqual([{ path: "/session/active/other-session", body: null }]);
    respond();
    await expect(revoke).toBeEnabled();
    expect(state.mutations).toEqual([{ path: "/session/active/other-session", body: null }]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("session revocation timeout unlocks controls and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { activeSessions: true, responseGate, responseGateOnce: true });
  page.on("dialog", (dialog) => dialog.accept());
  try {
    await page.addInitScript(() => {
      const nativeFetch = window.fetch.bind(window);
      let attempts = 0;
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (url.pathname !== "/control-api/session/active/other-session" || init?.method !== "DELETE") return pending;
        attempts++;
        if (attempts > 1) return pending;
        void pending.catch(() => undefined);
        return new Promise<Response>((_resolve, reject) => {
          setTimeout(() => reject(new Error("요청 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도하세요.")), 1_000);
        });
      }) as typeof window.fetch;
    });
    await page.goto("/");
    const revoke = page.getByRole("button", { name: "종료", exact: true });

    await revoke.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(revoke).toBeDisabled();
    await expect(page.getByRole("button", { name: "다른 세션 모두 종료", exact: true })).toBeDisabled();
    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible();
    await expect(revoke).toBeEnabled();

    respond();
    await revoke.click();
    await expect(page.getByText("선택한 세션을 종료했습니다.", { exact: true })).toBeVisible();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("@a11y OPERATOR environment revalidation errors preserve trigger focus", async ({ page }) => {
  const state = await fixture(page, "OPERATOR", { failure: "ENVIRONMENT_VALIDATION_REJECTED" });
  await fillRequest(page);

  const revalidate = page.getByRole("button", { name: "지금 재검증", exact: true });
  await revalidate.focus();
  await revalidate.press("Enter");
  const alert = page.getByRole("alert").filter({ hasText: "환경 재검증 요청이 거부되었습니다." });
  await expect(alert).toHaveAttribute("aria-live", "assertive");
  await expect(alert).toHaveAttribute("aria-atomic", "true");
  await expect(revalidate).toBeFocused();
  expect(state.mutations).toEqual([{ path: `/environments/${environmentId}/validate`, body: null }]);
  expect(state.unexpected).toEqual([]);
});

test("environment revalidation timeout keeps release fail-closed and permits one clean retry", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, responseGateOnce: true });
  try {
    await fillRequest(page);
    await page.evaluate(() => {
      const nativeFetch = window.fetch.bind(window);
      window.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.href);
        const pending = nativeFetch(input, init);
        if (!url.pathname.endsWith("/validate") || init?.method !== "POST") return pending;
        return new Promise<Response>((resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true });
          pending.then(resolve, reject);
        });
      }) as typeof window.fetch;
    });
    const revalidate = page.getByRole("button", { name: "지금 재검증", exact: true });
    const request = page.getByRole("button", { name: "릴리스 요청", exact: true });

    await revalidate.click();
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(page.getByRole("button", { name: "재검증 중…", exact: true })).toBeDisabled();
    await expect(request).toBeDisabled();

    await expect(page.getByRole("alert").filter({ hasText: "요청 시간이 초과되었습니다." })).toBeVisible({ timeout: 20_000 });
    await expect(revalidate).toBeEnabled();
    await expect(request).toBeDisabled();

    await revalidate.click();
    await expect(page.getByText("환경 재검증이 완료되었습니다.", { exact: true })).toBeVisible();
    await expect(request).toBeEnabled();
    expect(state.mutations).toHaveLength(2);
    expect(state.mutations[1]).toEqual(state.mutations[0]);
    expect(state.unexpected).toEqual([]);
  } finally {
    respond();
  }
});

test("environment revalidation and release request remain single-flight before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, failure: "ENVIRONMENT_VALIDATION_REJECTED" });
  try {
    await fillRequest(page);
    const revalidate = page.getByRole("button", { name: "지금 재검증", exact: true });
    const request = page.getByRole("button", { name: "릴리스 요청", exact: true });
    await expect(revalidate).toBeEnabled();
    await expect(request).toBeEnabled();

    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll("button"));
      const revalidateButton = buttons.find((button) => button.textContent === "지금 재검증");
      const requestButton = buttons.find((button) => button.textContent === "릴리스 요청");
      if (!revalidateButton || !requestButton) throw new Error("Release request controls are unavailable");
      revalidateButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      revalidateButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      requestButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations).toEqual([{ path: `/environments/${environmentId}/validate`, body: null }]);
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "환경 재검증 요청이 거부되었습니다." })).toBeVisible();
    await expect(revalidate).toBeEnabled();
    await expect(request).toBeDisabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
});

test("release request blocks environment revalidation before busy state renders", async ({ page }) => {
  let respond!: () => void;
  const responseGate = new Promise<void>((resolve) => { respond = resolve; });
  const state = await fixture(page, "OPERATOR", { responseGate, failure: "FORBIDDEN" });
  try {
    await fillRequest(page);
    const request = page.getByRole("button", { name: "릴리스 요청", exact: true });
    const revalidate = page.getByRole("button", { name: "지금 재검증", exact: true });
    await expect(request).toBeEnabled();
    await expect(revalidate).toBeEnabled();

    await request.evaluate((requestButton) => {
      const buttons = Array.from(document.querySelectorAll("button"));
      const revalidateButton = buttons.find((button) => button.textContent === "지금 재검증");
      if (!revalidateButton) throw new Error("Environment revalidation control is unavailable");
      const form = requestButton.closest("form");
      if (!form) throw new Error("Release request form is unavailable");
      form.requestSubmit(requestButton as HTMLButtonElement);
      form.requestSubmit(requestButton as HTMLButtonElement);
      revalidateButton.click();
    });

    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations[0].path).toBe("/releases");
    respond();
    await expect(page.getByRole("alert").filter({ hasText: "릴리스 요청이 거부되었습니다." })).toBeVisible();
    await expect(request).toBeEnabled();
    await expect(revalidate).toBeEnabled();
    expect(state.mutations).toHaveLength(1);
    expect(state.unexpected).toEqual([]);
  } finally { respond(); }
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
