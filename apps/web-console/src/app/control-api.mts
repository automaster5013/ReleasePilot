export type CsrfToken = { headerName: string; token: string };

export function mutationHeaders(csrf: CsrfToken, options: { idempotencyKey?: string; json?: boolean } = {}) {
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;
  if (options.json) headers["Content-Type"] = "application/json";
  return headers;
}

export function canDecideRelease(roles: string[], releaseStatus: string | undefined) {
  return roles.includes("APPROVER") && releaseStatus === "PENDING_APPROVAL";
}
