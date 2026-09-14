import assert from "node:assert/strict";
import test from "node:test";
import { mutationHeaders } from "./control-api.mts";

test("mutation headers include the server-selected CSRF header", () => {
  assert.deepEqual(mutationHeaders({ headerName: "X-CSRF-TOKEN", token: "token" }), {
    "X-CSRF-TOKEN": "token",
  });
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
