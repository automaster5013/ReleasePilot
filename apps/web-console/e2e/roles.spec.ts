import { expect, Page, test } from "@playwright/test";

const origin = "http://127.0.0.1:3100";
const serviceId = "11111111-1111-4111-8111-111111111111";
const environmentId = "22222222-2222-4222-8222-222222222222";
type Mutation = { path: string; body: Record<string, string | null> };

async function fixture(page: Page, role: string, options: { stale?: boolean; failure?: string } = {}) {
  let status = role === "OPERATOR" ? "ANALYZING" : "PENDING_APPROVAL";
  const mutations: Mutation[] = [];
  const unexpected: string[] = [];
  const audit: object[] = [];
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
    if (path === "/session/csrf") return json({ headerName: "X-CSRF-TOKEN", token: "role-fixture-csrf" });
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
