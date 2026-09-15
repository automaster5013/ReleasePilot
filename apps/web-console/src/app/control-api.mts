export type CsrfToken = { headerName: string; token: string };

export type CatalogItem = { id: string; name: string; status: string; key?: string; strategy?: string };
export type ReleaseSummary = { id: string; version: string; status: string; createdAt: string; context?: { serviceName: string; environmentName: string } };
export type AuditEventView = { id: string; eventType: string; actorType: string; actorId: string | null; occurredAt: string; correlationId: string; chainSequence: number | null; payloadJson?: string | null };
export type AuditChainVerification = { valid: boolean; verifiedEvents: number; failedEventId: string | null; headHash: string };
export type EnvironmentValidation = { environmentId: string; status: string; checkedAt: string; validUntil: string; checks: { code: string; outcome: string; message: string }[] };
export type PrometheusConnection = { id: string; name: string; baseUrl: string; secretRef: string | null; status: string; lastValidatedAt: string | null; queryTimeoutSeconds: number };
export type ClusterConnection = { id: string; name: string; apiServer: string; allowedNamespaces: string[]; secretRef: string; status: string; lastValidatedAt: string | null };
export type ClusterConnectionDraft = { name: string; apiServer: string; namespaces: string; secretRef: string };
export type PrometheusConnectionDraft = { name: string; baseUrl: string; secretRef: string; queryTimeoutSeconds: string };

export function selectableCatalogItems<T extends CatalogItem>(items: T[], activeStatuses = ["ACTIVE"]) {
  return items.filter((item) => activeStatuses.includes(item.status));
}

export function releaseOptionLabel(release: ReleaseSummary) {
  const created = new Date(release.createdAt);
  const date = Number.isNaN(created.getTime()) ? "날짜 미상" : created.toISOString().slice(0, 10);
  const target = release.context ? `${release.context.serviceName}/${release.context.environmentName} · ` : "";
  return `${target}${release.version} · ${release.status} · ${date}`;
}

export function auditEventLabel(event: AuditEventView) {
  const action = event.eventType.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
  const actor = event.actorType === "SYSTEM" ? "System" : event.actorId ? `User ${event.actorId.slice(0, 8)}` : "User";
  return `${action} · ${actor}`;
}

export function connectionAuditDetail(event: AuditEventView) {
  if (!event.payloadJson) return null;
  try {
    const payload = JSON.parse(event.payloadJson) as { status?: unknown; failureCode?: unknown };
    const status = typeof payload.status === "string" ? payload.status : null;
    const failure = typeof payload.failureCode === "string" && payload.failureCode !== "null" ? payload.failureCode : null;
    return [status, failure].filter(Boolean).join(" · ") || null;
  } catch { return null; }
}

export function canVerifyAudit(roles: string[]) {
  return roles.includes("OPERATOR");
}

export function auditIntegrityLabel(result: AuditChainVerification) {
  return result.valid ? `Verified · ${result.verifiedEvents} events` : `Integrity failure · event ${result.failedEventId?.slice(0, 8) ?? "unknown"}`;
}

export function environmentValidationSummary(result: EnvironmentValidation, now = Date.now()) {
  const validUntil = Date.parse(result.validUntil);
  if (!Number.isFinite(validUntil) || validUntil <= now) return "Validation expired";
  const failed = result.checks.filter((check) => check.outcome === "FAIL").length;
  const warnings = result.checks.filter((check) => check.outcome === "WARNING").length;
  return failed ? `${failed} failed checks` : warnings ? `${warnings} warnings` : `${result.checks.length} checks passed`;
}

export function canRevalidateEnvironment(roles: string[]) {
  return roles.includes("OPERATOR");
}

export function canManageConnections(roles: string[]) {
  return roles.includes("OPERATOR");
}

export function connectionValidationFresh(connection: Pick<PrometheusConnection, "status" | "lastValidatedAt">, now = Date.now()) {
  const validatedAt = connection.lastValidatedAt ? Date.parse(connection.lastValidatedAt) : Number.NaN;
  return connection.status === "ACTIVE" && Number.isFinite(validatedAt) && validatedAt > now - 6 * 60 * 60 * 1000;
}

export function connectionValidationLabel(connection: Pick<PrometheusConnection, "status" | "lastValidatedAt">, now = Date.now()) {
  if (connection.status !== "ACTIVE") return `${connection.status} · validation required`;
  const validatedAt = connection.lastValidatedAt ? Date.parse(connection.lastValidatedAt) : Number.NaN;
  if (!connectionValidationFresh(connection, now)) return "ACTIVE · validation expired";
  return `ACTIVE · validated ${new Date(validatedAt).toLocaleString("ko-KR")}`;
}

export type ConnectionFilter = "ALL" | "NEEDS_ATTENTION" | "ACTIVE" | "DISABLED";

export function filterConnections<T extends { name: string; status: string; lastValidatedAt: string | null }>(items: T[], query: string, filter: ConnectionFilter, now = Date.now()) {
  const term = query.trim().toLocaleLowerCase();
  return items.filter((item) => {
    if (term && !item.name.toLocaleLowerCase().includes(term)) return false;
    if (filter === "ALL") return true;
    if (filter === "NEEDS_ATTENTION") {
      return item.status === "INVALID" || item.status === "UNVERIFIED" || (item.status === "ACTIVE" && !connectionValidationFresh(item, now));
    }
    if (filter === "ACTIVE") return connectionValidationFresh(item, now);
    return item.status === filter;
  });
}

const secretReference = /^[A-Za-z0-9._:/-]+$/;
const namespaceName = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/;

export function parseNamespaces(value: string) {
  return [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))];
}

export function validateClusterConnectionDraft(draft: ClusterConnectionDraft) {
  if (!draft.name.trim() || draft.name.trim().length > 100) return "연결 이름은 1자 이상 100자 이하여야 합니다.";
  try { const url = new URL(draft.apiServer); if (url.protocol !== "https:" || draft.apiServer.length > 500) throw new Error(); }
  catch { return "Kubernetes API 주소는 유효한 HTTPS URL이어야 합니다."; }
  const namespaces = parseNamespaces(draft.namespaces);
  if (!namespaces.length || namespaces.length > 100 || namespaces.some((item) => !namespaceName.test(item))) return "Namespace는 쉼표로 구분한 유효한 Kubernetes 이름이어야 합니다.";
  if (!draft.secretRef.trim() || draft.secretRef.length > 255 || !secretReference.test(draft.secretRef)) return "Secret reference 형식을 확인하세요.";
  return null;
}

export function validatePrometheusConnectionDraft(draft: PrometheusConnectionDraft) {
  if (!draft.name.trim() || draft.name.trim().length > 100) return "연결 이름은 1자 이상 100자 이하여야 합니다.";
  try { const url = new URL(draft.baseUrl); if (!['http:', 'https:'].includes(url.protocol) || draft.baseUrl.length > 500) throw new Error(); }
  catch { return "Prometheus 주소는 유효한 HTTP(S) URL이어야 합니다."; }
  if (draft.secretRef && (draft.secretRef.length > 255 || !secretReference.test(draft.secretRef))) return "Secret reference 형식을 확인하세요.";
  const timeout = Number(draft.queryTimeoutSeconds);
  if (!Number.isInteger(timeout) || timeout < 1 || timeout > 120) return "Query timeout은 1초 이상 120초 이하여야 합니다.";
  return null;
}

export function environmentAllowsRelease(result: EnvironmentValidation | null, now = Date.now(), environmentId?: string) {
  if (environmentId !== undefined && result?.environmentId !== environmentId) return false;
  if (result === null || !["ACTIVE", "ACTIVE_WITH_WARNINGS"].includes(result.status)) return false;
  const validUntil = Date.parse(result.validUntil);
  return Number.isFinite(validUntil) && validUntil > now;
}

export async function readinessMutationHeaders(result: EnvironmentValidation | null, csrfProvider: () => Promise<CsrfToken>, options: { idempotencyKey?: string; json?: boolean }, requireReady = true, environmentId?: string) {
  const check = () => { if (requireReady && !environmentAllowsRelease(result, Date.now(), environmentId)) throw new Error("환경 검증이 만료되었거나 사용할 수 없습니다. 재검증 후 다시 시도하세요."); };
  check();
  const csrf = await csrfProvider();
  check();
  return mutationHeaders(csrf, options);
}

export function createMutationGate() {
  let busy = false;
  return {
    tryAcquire() { if (busy) return false; busy = true; return true; },
    release() { busy = false; },
  };
}

export function createLatestRequestGuard() {
  let generation = 0;
  const snapshot = () => { const request = generation; return () => request === generation; };
  return { begin() { generation++; return snapshot(); }, snapshot };
}

export function mutationHeaders(csrf: CsrfToken, options: { idempotencyKey?: string; json?: boolean } = {}) {
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;
  if (options.json) headers["Content-Type"] = "application/json";
  return headers;
}

export function canDecideRelease(roles: string[], releaseStatus: string | undefined) {
  return roles.includes("APPROVER") && releaseStatus === "PENDING_APPROVAL";
}

export function approvalReadinessMessage(code: string | undefined) {
  if (code === "ENVIRONMENT_VALIDATION_STALE") return "환경 검증이 만료되어 승인할 수 없습니다. 운영자 재검증 후 다시 승인하세요.";
  if (code === "ENVIRONMENT_NOT_ACTIVE") return "환경이 최신 검증을 통과하지 못해 승인할 수 없습니다. 운영자 점검이 필요합니다.";
  if (code === "CLUSTER_CONNECTION_NOT_ACTIVE") return "대상 Kubernetes 연결이 비활성 상태여서 승인할 수 없습니다. 운영자 연결 검증이 필요합니다.";
  if (code === "CLUSTER_CONNECTION_VALIDATION_STALE") return "대상 Kubernetes 연결 검증이 만료되어 승인할 수 없습니다. 운영자 연결 재검증 후 다시 승인하세요.";
  if (code === "PROMETHEUS_CONNECTION_NOT_ACTIVE") return "대상 Prometheus 연결이 비활성 상태여서 승인할 수 없습니다. 운영자 연결 검증이 필요합니다.";
  if (code === "PROMETHEUS_CONNECTION_VALIDATION_STALE") return "대상 Prometheus 연결 검증이 만료되어 승인할 수 없습니다. 운영자 연결 재검증 후 다시 승인하세요.";
  return null;
}

export function releaseRequestReadinessMessage(code: string | undefined) {
  if (code === "ENVIRONMENT_VALIDATION_STALE") return "환경 검증이 만료되었습니다. 운영자 재검증 후 다시 요청하세요.";
  if (code === "ENVIRONMENT_NOT_ACTIVE") return "환경이 최신 검증을 통과하지 못했습니다. 운영자 점검 후 다시 요청하세요.";
  if (code === "CLUSTER_CONNECTION_NOT_ACTIVE") return "대상 Kubernetes 연결이 비활성 상태입니다. 운영자 연결 검증 후 다시 요청하세요.";
  if (code === "CLUSTER_CONNECTION_VALIDATION_STALE") return "대상 Kubernetes 연결 검증이 만료되었습니다. 운영자 연결 재검증 후 다시 요청하세요.";
  if (code === "PROMETHEUS_CONNECTION_NOT_ACTIVE") return "대상 Prometheus 연결이 비활성 상태입니다. 운영자 연결 검증 후 다시 요청하세요.";
  if (code === "PROMETHEUS_CONNECTION_VALIDATION_STALE") return "대상 Prometheus 연결 검증이 만료되었습니다. 운영자 연결 재검증 후 다시 요청하세요.";
  return null;
}

export function approvalReadinessLabel(result: EnvironmentValidation | null, now = Date.now()) {
  if (result === null) return "Readiness unavailable";
  if (!environmentAllowsRelease(result, now)) return `${result.status} · revalidation required`;
  return `${result.status} · valid until ${new Date(result.validUntil).toLocaleString("ko-KR")}`;
}

export type ReleaseDraft = {
  serviceId: string;
  environmentId: string;
  version: string;
  imageRepository: string;
  imageDigest: string;
  changeSummary: string;
  commitSha: string;
  pipelineUrl: string;
  requestedPolicyVersionId: string;
};

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function canRequestRelease(roles: string[]) {
  return roles.some((role) => ["DEVELOPER", "APPROVER", "OPERATOR"].includes(role));
}

export function validateReleaseDraft(draft: ReleaseDraft) {
  if (!uuid.test(draft.serviceId) || !uuid.test(draft.environmentId)) return "Service와 Environment ID는 올바른 UUID여야 합니다.";
  if (draft.requestedPolicyVersionId && !uuid.test(draft.requestedPolicyVersionId)) return "Policy Version ID는 올바른 UUID여야 합니다.";
  if (!draft.version.trim() || draft.version.trim().length > 100) return "버전은 1자 이상 100자 이하여야 합니다.";
  if (!draft.imageRepository.trim() || draft.imageRepository.trim().length > 500) return "이미지 저장소는 1자 이상 500자 이하여야 합니다.";
  if (!/^sha256:[a-f0-9]{64}$/.test(draft.imageDigest)) return "이미지 digest는 sha256: 뒤에 소문자 64자리여야 합니다.";
  if (!draft.changeSummary.trim() || draft.changeSummary.trim().length > 2000) return "변경 요약은 1자 이상 2000자 이하여야 합니다.";
  if (!/^[a-fA-F0-9]{40}$/.test(draft.commitSha)) return "Commit SHA는 40자리여야 합니다.";
  try {
    const url = new URL(draft.pipelineUrl);
    if (!["http:", "https:"].includes(url.protocol) || draft.pipelineUrl.length > 1000) throw new Error();
  } catch {
    return "Pipeline URL은 유효한 HTTP(S) 주소여야 합니다.";
  }
  return null;
}
