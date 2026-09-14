import assert from "node:assert/strict";
import test from "node:test";
import { canDecideRelease, mutationHeaders } from "./control-api.mts";

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
