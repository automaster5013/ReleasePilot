import assert from "node:assert/strict";
import test from "node:test";
import { canManageSessions, formatSessionTime } from "./session-management.mts";

test("shared demo users cannot manage sessions", () => {
  assert.equal(canManageSessions({ id: "1", displayName: "Demo", roles: ["VIEWER"], demo: true }), false);
  assert.equal(canManageSessions({ id: "2", displayName: "Operator", roles: ["OPERATOR"], demo: false }), true);
});

test("invalid session timestamps have a stable label", () => {
  assert.equal(formatSessionTime("invalid"), "알 수 없음");
});
