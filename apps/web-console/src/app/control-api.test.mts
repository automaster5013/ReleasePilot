import assert from "node:assert/strict";
import test from "node:test";
import { canDecideRelease, canRequestRelease, mutationHeaders, releaseOptionLabel, selectableCatalogItems, validateReleaseDraft } from "./control-api.mts";

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
