import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import test from "node:test";
import { contentSecurityPolicy } from "./security-policy.mts";

test("production scripts require a nonce without unsafe-inline or unsafe-eval", () => {
  const nonce = randomBytes(32).toString("base64");
  const policy = contentSecurityPolicy(nonce);
  const scripts = policy.split("; ").find((directive) => directive.startsWith("script-src"))!;
  assert.ok(scripts.includes(`'nonce-${nonce}'`));
  assert.ok(scripts.includes("'strict-dynamic'"));
  assert.ok(!scripts.includes("'unsafe-inline'"));
  assert.ok(!scripts.includes("'unsafe-eval'"));
  for (const directive of ["connect-src 'self'", "object-src 'none'", "frame-ancestors 'none'", "base-uri 'self'", "form-action 'self'"]) assert.ok(policy.includes(directive));
});

test("development evaluation does not loosen production policy", () => {
  assert.ok(contentSecurityPolicy("YWJj", true).includes("'unsafe-eval'"));
  assert.ok(!contentSecurityPolicy("YWJj").includes("'unsafe-eval'"));
});

test("nonce values cannot inject directives", () => {
  for (const nonce of ["", "x'; script-src *", "<script>", "abc\nxyz"]) assert.throws(() => contentSecurityPolicy(nonce));
  assert.notEqual(contentSecurityPolicy(randomBytes(32).toString("base64")), contentSecurityPolicy(randomBytes(32).toString("base64")));
});
