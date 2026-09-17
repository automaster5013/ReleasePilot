import assert from "node:assert/strict";
import test from "node:test";
import { clusterConnectionDraftIssue, createLatestRequestGuard, createMutationGate, fetchWithTimeout, readinessMutationHeaders, approvalReadinessLabel, approvalReadinessMessage, auditEventLabel, auditIntegrityLabel, canDecideRelease, canManageConnections, canRequestRelease, canRevalidateEnvironment, canVerifyAudit, connectionAuditDetail, connectionValidationLabel, environmentAllowsRelease, environmentValidationSummary, filterConnections, mutationHeaders, parseNamespaces, prometheusConnectionDraftIssue, releaseDraftIssue, releaseOptionLabel, releaseRequestReadinessMessage, runWithBrowserLock, selectableCatalogItems, validateClusterConnectionDraft, validatePrometheusConnectionDraft, validateReleaseDraft } from "./control-api.mts";

test("latest release request rejects delayed data, readiness and errors from older loads", async () => {
  const guard = createLatestRequestGuard();
  const first = guard.begin();
  assert.equal(first(), true);
  const second = guard.begin();
  await Promise.resolve();
  assert.equal(second(), true);
  assert.equal(first(), false);
  const third = guard.begin();
  assert.equal(second(), false);
  assert.equal(third(), true);
});

test("environment A-B-A navigation and manual validation invalidate older requests", () => {
  const guard = createLatestRequestGuard();
  const originalA = guard.begin();
  guard.begin(); // select B
  guard.begin(); // return to A
  const latestA = guard.begin();
  assert.equal(originalA(), false);
  assert.equal(latestA(), true);
  const manual = guard.begin();
  assert.equal(latestA(), false);
  assert.equal(manual(), true);
  guard.begin(); // selection changes while manual request is pending
  assert.equal(manual(), false);
});

test("operation snapshots preserve a load until selection changes, including the same release", () => {
  const guard = createLatestRequestGuard();
  const loaded = guard.begin();
  const approve = guard.snapshot();
  const audit = guard.snapshot();
  assert.equal(loaded(), true);
  assert.equal(approve(), true);
  assert.equal(audit(), true);
  const next = guard.begin();
  assert.equal(approve(), false);
  assert.equal(audit(), false);
  assert.equal(next(), true);
  assert.equal(guard.snapshot()(), true);
});

test("release mutation gate blocks same-tick submissions and recovers after failure", async () => {
  const gate = createMutationGate();
  assert.equal(gate.tryAcquire(), true);
  assert.equal(gate.tryAcquire(), false);
  try { await Promise.reject(new Error("network failure")); } catch { /* expected */ } finally { gate.release(); }
  assert.equal(gate.tryAcquire(), true);
  assert.equal(gate.tryAcquire(), false);
  gate.release();
  assert.equal(gate.tryAcquire(), true);
});

test("browser lock rejects a competing tab and releases after completion", async () => {
  let held = false;
  const manager = {
    request: async (_name: string, _options: LockOptions, callback: (lock: Lock | null) => Promise<unknown>) => {
      if (held) return callback(null);
      held = true;
      try { return await callback({ name: "releasepilot:session-demo", mode: "exclusive" } as Lock); }
      finally { held = false; }
    },
  } as LockManager;
  const values = new Map<string, string>();
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
    clear: () => values.clear(),
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() { return values.size; },
  } as Storage;
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  const first = runWithBrowserLock("releasepilot:session-demo", async () => { await gate; return "started"; }, manager, storage, 1000);
  await Promise.resolve();
  const competing = await runWithBrowserLock("releasepilot:session-demo", async () => "duplicate", manager, storage, 1001);
  assert.deepEqual(competing, { acquired: false });
  release();
  assert.deepEqual(await first, { acquired: true, value: "started" });
  assert.deepEqual(await runWithBrowserLock("releasepilot:session-demo", async () => "retry", manager, storage, 1002), { acquired: true, value: "retry" });
});

test("timed fetch aborts stalled requests with a retryable message and preserves other failures", async () => {
  let observedSignal: AbortSignal | undefined;
  const stalled = ((_input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
    observedSignal = init?.signal ?? undefined;
    observedSignal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true });
  })) as typeof fetch;
  await assert.rejects(fetchWithTimeout("https://example.invalid", {}, 5, stalled), /요청 시간이 초과되었습니다/);
  assert.equal(observedSignal?.aborted, true);

  const networkFailure = new Error("network failure");
  const rejected = (() => Promise.reject(networkFailure)) as typeof fetch;
  await assert.rejects(fetchWithTimeout("https://example.invalid", {}, 50, rejected), networkFailure);
  await assert.rejects(fetchWithTimeout("https://example.invalid", {}, 0, rejected), /0보다 커야/);
});

test("revalidation and release requests share one gate and failed validation stays fail-closed", () => {
  const gate = createMutationGate();
  const previous = { environmentId: "env", status: "ACTIVE", checkedAt: "2026-09-15T00:00:00Z", validUntil: "2099-01-01T00:00:00Z", checks: [] };
  assert.equal(environmentAllowsRelease(previous), true);
  assert.equal(gate.tryAcquire(), true); // revalidation starts
  assert.equal(gate.tryAcquire(), false); // release cannot race it
  assert.equal(environmentAllowsRelease(null), false); // old result cleared
  gate.release(); // failure releases only the lock, not the old result
  assert.equal(environmentAllowsRelease(null), false);
  assert.equal(gate.tryAcquire(), true); // retry remains possible
  assert.equal(gate.tryAcquire(), false); // release blocks revalidation too
  gate.release();
});

test("mutation readiness is checked again after CSRF waits and rejection stays available", async () => {
  const originalNow = Date.now;
  let now = Date.parse("2026-09-15T05:59:59Z");
  const result = { environmentId: "env", status: "ACTIVE", checkedAt: "2026-09-15T00:00:00Z", validUntil: "2026-09-15T06:00:00Z", checks: [] };
  let calls = 0;
  const csrf = async () => { calls++; now += 1000; return { headerName: "X-CSRF-TOKEN", token: "test" }; };
  Date.now = () => now;
  try {
    await assert.rejects(readinessMutationHeaders(result, csrf, { json: true }), /환경 검증/);
    assert.equal(calls, 1);
    await assert.rejects(readinessMutationHeaders(null, csrf, {}), /환경 검증/);
    assert.equal(calls, 1);
    assert.deepEqual(await readinessMutationHeaders(null, csrf, { json: true }, false), { "X-CSRF-TOKEN": "test", "Content-Type": "application/json" });
  } finally { Date.now = originalNow; }
});

test("request and approval readiness expire together on the display clock", () => {
  const expiry = Date.parse("2026-09-15T06:00:00Z");
  const result = { environmentId: "env", status: "ACTIVE", checkedAt: "2026-09-15T00:00:00Z", validUntil: "2026-09-15T06:00:00Z", checks: [] };
  assert.equal(environmentAllowsRelease(result, expiry - 1), true);
  assert.equal(environmentAllowsRelease(result, expiry), false);
  assert.equal(environmentAllowsRelease(result, expiry + 1), false);
  assert.equal(environmentValidationSummary(result, expiry), "Validation expired");
  assert.equal(approvalReadinessLabel(result, expiry), "ACTIVE · revalidation required");
  assert.equal(environmentAllowsRelease({ ...result, validUntil: "invalid" }, expiry), false);
});

test("fresh validation from a different environment cannot authorize a release", async () => {
  const result = { environmentId: "old-environment", status: "ACTIVE", checkedAt: "2026-09-15T00:00:00Z", validUntil: "2099-01-01T00:00:00Z", checks: [] };
  assert.equal(environmentAllowsRelease(result, Date.now(), "old-environment"), true);
  assert.equal(environmentAllowsRelease(result, Date.now(), "new-environment"), false);
  assert.equal(environmentAllowsRelease(null, Date.now(), "new-environment"), false);
  let csrfCalls = 0;
  const csrf = async () => { csrfCalls++; return { headerName: "X-CSRF-TOKEN", token: "test" }; };
  await assert.rejects(readinessMutationHeaders(result, csrf, {}, true, "new-environment"), /환경 검증/);
  assert.equal(csrfCalls, 0);
});

test("mutation headers include the server-selected CSRF header", () => {
  assert.deepEqual(mutationHeaders({ headerName: "X-CSRF-TOKEN", token: "token" }), {
    "X-CSRF-TOKEN": "token",
  });
});

test("malformed CSRF responses are rejected before constructing mutation headers", () => {
  const invalid = [null, {}, { headerName: "", token: "token" }, { headerName: "bad header", token: "token" },
    { headerName: "X-CSRF-TOKEN", token: " " }, { headerName: "X-CSRF-TOKEN", token: 42 },
    { headerName: "X-CSRF-TOKEN", token: "token\r\nInjected: value" }];
  for (const csrf of invalid) assert.throws(() => mutationHeaders(csrf as Parameters<typeof mutationHeaders>[0]), /보안 토큰 응답/);
});

test("only approvers can decide a pending release", () => {
  assert.equal(canDecideRelease(["APPROVER"], "PENDING_APPROVAL"), true);
  assert.equal(canDecideRelease(["OPERATOR"], "PENDING_APPROVAL"), false);
  assert.equal(canDecideRelease(["APPROVER"], "APPROVED"), false);
});

test("approval readiness failures tell approvers how to recover", () => {
  assert.match(approvalReadinessMessage("ENVIRONMENT_VALIDATION_STALE") ?? "", /재검증/);
  assert.match(approvalReadinessMessage("ENVIRONMENT_NOT_ACTIVE") ?? "", /점검/);
  assert.match(approvalReadinessMessage("CLUSTER_CONNECTION_NOT_ACTIVE") ?? "", /Kubernetes 연결/);
  assert.match(approvalReadinessMessage("CLUSTER_CONNECTION_VALIDATION_STALE") ?? "", /연결 재검증/);
  assert.match(approvalReadinessMessage("PROMETHEUS_CONNECTION_NOT_ACTIVE") ?? "", /Prometheus 연결/);
  assert.match(approvalReadinessMessage("PROMETHEUS_CONNECTION_VALIDATION_STALE") ?? "", /Prometheus 연결.*재검증/);
  assert.equal(approvalReadinessMessage("UNKNOWN"), null);
  assert.equal(approvalReadinessLabel(null), "Readiness unavailable");
  const base = { environmentId: "env", checkedAt: "2026-09-15T00:00:00Z", checks: [] };
  assert.equal(approvalReadinessLabel({ ...base, status: "INVALID", validUntil: "2099-01-01T00:00:00Z" }), "INVALID · revalidation required");
  assert.equal(approvalReadinessLabel({ ...base, status: "ACTIVE", validUntil: "2000-01-01T00:00:00Z" }), "ACTIVE · revalidation required");
  assert.match(approvalReadinessLabel({ ...base, status: "ACTIVE", validUntil: "2099-01-01T00:00:00Z" }), /^ACTIVE · valid until /);
});

test("release readiness failures tell developers how to recover", () => {
  assert.match(releaseRequestReadinessMessage("ENVIRONMENT_VALIDATION_STALE") ?? "", /환경 검증.*재검증/);
  assert.match(releaseRequestReadinessMessage("ENVIRONMENT_NOT_ACTIVE") ?? "", /운영자 점검/);
  assert.match(releaseRequestReadinessMessage("CLUSTER_CONNECTION_NOT_ACTIVE") ?? "", /연결 검증/);
  assert.match(releaseRequestReadinessMessage("CLUSTER_CONNECTION_VALIDATION_STALE") ?? "", /연결 재검증/);
  assert.match(releaseRequestReadinessMessage("PROMETHEUS_CONNECTION_NOT_ACTIVE") ?? "", /Prometheus 연결/);
  assert.match(releaseRequestReadinessMessage("PROMETHEUS_CONNECTION_VALIDATION_STALE") ?? "", /Prometheus 연결.*재검증/);
  assert.equal(releaseRequestReadinessMessage("UNKNOWN"), null);
});

test("operator mutations include JSON and idempotency headers", () => {
  assert.deepEqual(mutationHeaders({ headerName: "X-XSRF-TOKEN", token: "csrf" }, {
    idempotencyKey: "operation-123456", json: true,
  }), {
    "X-XSRF-TOKEN": "csrf",
    "Idempotency-Key": "operation-123456",
    "Content-Type": "application/json",
  });
});

test("developer-capable roles can request a release", () => {
  assert.equal(canRequestRelease(["VIEWER"]), false);
  assert.equal(canRequestRelease(["DEVELOPER"]), true);
  assert.equal(canRequestRelease(["APPROVER"]), true);
  assert.equal(canRequestRelease(["OPERATOR"]), true);
});

test("release catalog only offers active and validated choices", () => {
  const items = [
    { id: "1", name: "Production", status: "ACTIVE" },
    { id: "2", name: "Staging", status: "ACTIVE_WITH_WARNINGS" },
    { id: "3", name: "Broken", status: "INVALID" },
  ];
  assert.deepEqual(selectableCatalogItems(items).map((item) => item.id), ["1"]);
  assert.deepEqual(selectableCatalogItems(items, ["ACTIVE", "ACTIVE_WITH_WARNINGS"]).map((item) => item.id), ["1", "2"]);
});

test("recent release choices have a stable compact label", () => {
  assert.equal(releaseOptionLabel({ id: "1", version: "v2.1.0", status: "PENDING_APPROVAL", createdAt: "2026-09-15T00:00:00Z" }), "v2.1.0 · PENDING_APPROVAL · 2026-09-15");
  assert.equal(releaseOptionLabel({ id: "3", version: "v2.1.0", status: "APPROVED", createdAt: "2026-09-15T00:00:00Z", context: { serviceName: "Checkout", environmentName: "production" } }), "Checkout/production · v2.1.0 · APPROVED · 2026-09-15");
  assert.equal(releaseOptionLabel({ id: "2", version: "v1", status: "FAILED", createdAt: "invalid" }), "v1 · FAILED · 날짜 미상");
});

test("audit events have readable action and privacy-safe actor labels", () => {
  const base = { id: "1", occurredAt: "2026-09-15T00:00:00Z", correlationId: "correlation", chainSequence: 1 };
  assert.equal(auditEventLabel({ ...base, eventType: "RELEASE_APPROVED", actorType: "USER", actorId: "12345678-abcd" }), "Release Approved · User 12345678");
  assert.equal(auditEventLabel({ ...base, eventType: "ROLLOUT_PROMOTE_COMPLETED", actorType: "SYSTEM", actorId: null }), "Rollout Promote Completed · System");
});

test("connection audit details expose stable status codes without unsafe payload fallback", () => {
  const base = { id: "1", eventType: "CLUSTER_CONNECTION_VALIDATED", actorType: "USER", actorId: "12345678-abcd", occurredAt: "2026-09-15T00:00:00Z", correlationId: "correlation", chainSequence: 1 };
  assert.equal(connectionAuditDetail({ ...base, payloadJson: '{"status":"INVALID","failureCode":"SECRET_UNAVAILABLE"}' }), "INVALID · SECRET_UNAVAILABLE");
  assert.equal(connectionAuditDetail({ ...base, payloadJson: '{"status":"ACTIVE","failureCode":"null"}' }), "ACTIVE");
  assert.equal(connectionAuditDetail({ ...base, payloadJson: "not-json" }), null);
  assert.equal(connectionAuditDetail(base), null);
});

test("audit integrity verification is operator-only and has stable labels", () => {
  assert.equal(canVerifyAudit(["VIEWER"]), false);
  assert.equal(canVerifyAudit(["OPERATOR"]), true);
  assert.equal(auditIntegrityLabel({ valid: true, verifiedEvents: 42, failedEventId: null, headHash: "abc" }), "Verified · 42 events");
  assert.equal(auditIntegrityLabel({ valid: false, verifiedEvents: 7, failedEventId: "12345678-abcd", headHash: "abc" }), "Integrity failure · event 12345678");
});

test("environment validation summaries prioritize failures and warnings", () => {
  const base = { environmentId: "env", status: "ACTIVE", checkedAt: "2026-09-15T00:00:00Z", validUntil: "2099-01-01T00:00:00Z" };
  assert.equal(environmentValidationSummary({ ...base, checks: [{ code: "A", outcome: "PASS", message: "ok" }] }), "1 checks passed");
  assert.equal(environmentValidationSummary({ ...base, checks: [{ code: "A", outcome: "WARNING", message: "slow" }] }), "1 warnings");
  assert.equal(environmentValidationSummary({ ...base, checks: [{ code: "A", outcome: "FAIL", message: "denied" }, { code: "B", outcome: "WARNING", message: "slow" }] }), "1 failed checks");
});

test("environment revalidation is operator-only and failed readiness blocks releases", () => {
  assert.equal(canRevalidateEnvironment(["DEVELOPER"]), false);
  assert.equal(canRevalidateEnvironment(["OPERATOR"]), true);
  assert.equal(environmentAllowsRelease(null), false);
  const base = { environmentId: "env", checkedAt: "2026-09-15T00:00:00Z", checks: [] };
  assert.equal(environmentAllowsRelease({ ...base, status: "INVALID", validUntil: "2099-01-01T00:00:00Z" }), false);
  assert.equal(environmentAllowsRelease({ ...base, status: "ACTIVE_WITH_WARNINGS", validUntil: "2099-01-01T00:00:00Z" }), true);
  assert.equal(environmentAllowsRelease({ ...base, status: "ACTIVE", validUntil: "2000-01-01T00:00:00Z" }), false);
  assert.equal(environmentAllowsRelease({ ...base, status: "ACTIVE", validUntil: "invalid" }), false);
  assert.equal(environmentValidationSummary({ ...base, status: "ACTIVE", validUntil: "2000-01-01T00:00:00Z" }), "Validation expired");
});

test("Prometheus connection management is operator-only and surfaces stale validation", () => {
  assert.equal(canManageConnections(["VIEWER"]), false);
  assert.equal(canManageConnections(["OPERATOR"]), true);
  const base = { id: "1", name: "production", baseUrl: "https://prometheus.example", queryTimeoutSeconds: 10 };
  assert.equal(connectionValidationLabel({ ...base, status: "INVALID", lastValidatedAt: null }, Date.parse("2026-09-15T12:00:00Z")), "INVALID · validation required");
  assert.equal(connectionValidationLabel({ ...base, status: "ACTIVE", lastValidatedAt: "2026-09-15T05:59:59Z" }, Date.parse("2026-09-15T12:00:00Z")), "ACTIVE · validation expired");
  assert.match(connectionValidationLabel({ ...base, status: "ACTIVE", lastValidatedAt: "2026-09-15T11:00:00Z" }, Date.parse("2026-09-15T12:00:00Z")), /^ACTIVE · validated /);
});

test("Kubernetes connections share the same fail-closed freshness label", () => {
  const cluster = { status: "ACTIVE", lastValidatedAt: "2026-09-15T03:00:00Z" };
  assert.equal(connectionValidationLabel(cluster, Date.parse("2026-09-15T10:00:00Z")), "ACTIVE · validation expired");
  assert.match(connectionValidationLabel(cluster, Date.parse("2026-09-15T04:00:00Z")), /^ACTIVE · validated /);
});

test("connection search and status filters surface stale or failed targets", () => {
  const now = Date.parse("2026-09-15T12:00:00Z");
  const items = [
    { name: "Production East", status: "ACTIVE", lastValidatedAt: "2026-09-15T11:00:00Z" },
    { name: "Production West", status: "ACTIVE", lastValidatedAt: "2026-09-15T05:00:00Z" },
    { name: "Staging", status: "INVALID", lastValidatedAt: "2026-09-15T11:00:00Z" },
    { name: "Retired", status: "DISABLED", lastValidatedAt: null },
  ];
  assert.deepEqual(filterConnections(items, "production", "ALL", now).map((item) => item.name), ["Production East", "Production West"]);
  assert.deepEqual(filterConnections(items, "", "NEEDS_ATTENTION", now).map((item) => item.name), ["Production West", "Staging"]);
  assert.deepEqual(filterConnections(items, "", "ACTIVE", now).map((item) => item.name), ["Production East"]);
  assert.deepEqual(filterConnections(items, "ret", "DISABLED", now).map((item) => item.name), ["Retired"]);
});

test("connection freshness moves healthy targets to attention at the expiry boundary", () => {
  const checkedAt = Date.parse("2026-09-15T06:00:00Z");
  const items = [{ name: "boundary", status: "ACTIVE", lastValidatedAt: new Date(checkedAt).toISOString() }];
  assert.equal(filterConnections(items, "", "ACTIVE", checkedAt + 21_599_999).length, 1);
  assert.equal(filterConnections(items, "", "ACTIVE", checkedAt + 21_600_000).length, 0);
  assert.equal(filterConnections(items, "", "NEEDS_ATTENTION", checkedAt + 21_600_000).length, 1);
  for (const lastValidatedAt of [null, "invalid"]) {
    assert.equal(filterConnections([{ ...items[0], lastValidatedAt }], "", "ACTIVE", checkedAt).length, 0);
    assert.equal(filterConnections([{ ...items[0], lastValidatedAt }], "", "NEEDS_ATTENTION", checkedAt).length, 1);
  }
});

test("connection drafts are validated before operator mutations", () => {
  const cluster = { name: "production", apiServer: "https://kubernetes.example", namespaces: "releasepilot, releasepilot, monitoring", secretRef: "env:KUBERNETES_TOKEN" };
  assert.equal(validateClusterConnectionDraft(cluster), null);
  assert.deepEqual(parseNamespaces(cluster.namespaces), ["releasepilot", "monitoring"]);
  assert.match(validateClusterConnectionDraft({ ...cluster, apiServer: "http://kubernetes.example" }) ?? "", /HTTPS/);
  assert.match(validateClusterConnectionDraft({ ...cluster, namespaces: "UPPER_CASE" }) ?? "", /Namespace/);
  const prometheus = { name: "metrics", baseUrl: "http://prometheus:9090", secretRef: "", queryTimeoutSeconds: "15" };
  assert.equal(validatePrometheusConnectionDraft(prometheus), null);
  assert.match(validatePrometheusConnectionDraft({ ...prometheus, baseUrl: "ftp://prometheus" }) ?? "", /HTTP/);
  assert.match(validatePrometheusConnectionDraft({ ...prometheus, queryTimeoutSeconds: "121" }) ?? "", /120/);
  assert.match(validatePrometheusConnectionDraft({ ...prometheus, secretRef: "bad secret" }) ?? "", /Secret/);
});

test("release draft validation fails closed before mutation", () => {
  const valid = {
    serviceId: "123e4567-e89b-42d3-a456-426614174000",
    environmentId: "123e4567-e89b-42d3-a456-426614174001",
    version: "v1.2.3",
    imageRepository: "registry.example/releasepilot",
    imageDigest: `sha256:${"a".repeat(64)}`,
    changeSummary: "Ship the release request console",
    commitSha: "b".repeat(40),
    pipelineUrl: "https://github.com/example/actions/runs/1",
    requestedPolicyVersionId: "",
  };
  assert.equal(validateReleaseDraft(valid), null);
  assert.match(validateReleaseDraft({ ...valid, imageDigest: "latest" }) ?? "", /digest/);
  assert.match(validateReleaseDraft({ ...valid, pipelineUrl: "javascript:alert(1)" }) ?? "", /HTTP/);
  assert.deepEqual(releaseDraftIssue({ ...valid, requestedPolicyVersionId: "invalid" }), { field: "requestedPolicyVersionId", message: "Policy Version ID는 올바른 UUID여야 합니다." });
});

test("connection draft validation identifies the first invalid field", () => {
  assert.deepEqual(clusterConnectionDraftIssue({ name: "cluster", apiServer: "https://cluster.example", namespaces: "releasepilot", secretRef: "invalid ref" }), { field: "secretRef", message: "Secret reference 형식을 확인하세요." });
  assert.deepEqual(prometheusConnectionDraftIssue({ name: "metrics", baseUrl: "https://metrics.example", secretRef: "", queryTimeoutSeconds: "121" }), { field: "queryTimeoutSeconds", message: "Query timeout은 1초 이상 120초 이하여야 합니다." });
});
