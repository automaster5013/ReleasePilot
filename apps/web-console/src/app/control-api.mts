export type CsrfToken = { headerName: string; token: string };

export type CatalogItem = { id: string; name: string; status: string; key?: string; strategy?: string };
export type ReleaseSummary = { id: string; version: string; status: string; createdAt: string };

export function selectableCatalogItems<T extends CatalogItem>(items: T[], activeStatuses = ["ACTIVE"]) {
  return items.filter((item) => activeStatuses.includes(item.status));
}

export function releaseOptionLabel(release: ReleaseSummary) {
  const created = new Date(release.createdAt);
  const date = Number.isNaN(created.getTime()) ? "날짜 미상" : created.toISOString().slice(0, 10);
  return `${release.version} · ${release.status} · ${date}`;
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
