import assert from "node:assert/strict";
import test from "node:test";
import { approvalReadinessLabel, approvalReadinessMessage, auditEventLabel, auditIntegrityLabel, canDecideRelease, canManageConnections, canRequestRelease, canRevalidateEnvironment, canVerifyAudit, connectionValidationLabel, environmentAllowsRelease, environmentValidationSummary, mutationHeaders, releaseOptionLabel, releaseRequestReadinessMessage, selectableCatalogItems, validateReleaseDraft } from "./control-api.mts";

test("mutation headers include the server-selected CSRF header", () => {
  assert.deepEqual(mutationHeaders({ headerName: "X-CSRF-TOKEN", token: "token" }), {
    "X-CSRF-TOKEN": "token",
  });
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
});
