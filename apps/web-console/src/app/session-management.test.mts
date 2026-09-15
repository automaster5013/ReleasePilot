import assert from "node:assert/strict";
import test from "node:test";
import { canManageSessions, formatSessionTime, sessionConnectionLabel } from "./session-management.mts";

test("shared demo users cannot manage sessions", () => {
  assert.equal(canManageSessions({ id: "1", displayName: "Demo", roles: ["VIEWER"], demo: true }), false);
  assert.equal(canManageSessions({ id: "2", displayName: "Operator", roles: ["OPERATOR"], demo: false }), true);
});

test("invalid session timestamps have a stable label", () => {
  assert.equal(formatSessionTime("invalid"), "알 수 없음");
});

test("restored sessions derive their banner from authenticated identity", () => {
  const demo = { id: "1", displayName: "Demo", roles: ["VIEWER"], demo: true };
  const operator = { id: "2", displayName: "Operator", roles: ["OPERATOR"], demo: false };
  assert.equal(sessionConnectionLabel(null, "DEMO SNAPSHOT"), "DEMO SNAPSHOT");
  assert.equal(sessionConnectionLabel(demo, "DEMO SNAPSHOT"), "DEMO · VIEW ONLY");
  assert.equal(sessionConnectionLabel(operator, "DEMO SNAPSHOT"), "SIGNED IN");
  assert.equal(sessionConnectionLabel(operator, "DEMO · VIEW ONLY"), "SIGNED IN");
});

test("stream labels require both a session and a selected release", () => {
  const viewer = { id: "1", displayName: "Demo", roles: ["VIEWER"], demo: true };
  for (const status of ["LIVE", "RECONNECTING"]) {
    assert.equal(sessionConnectionLabel(viewer, status, true), status);
    assert.equal(sessionConnectionLabel(viewer, status, false), "DEMO · VIEW ONLY");
    assert.equal(sessionConnectionLabel(null, status, true), "DEMO SNAPSHOT");
  }
  assert.equal(canManageSessions(viewer), false);
});
