"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import styles from "./page.module.css";
import sessionStyles from "./session.module.css";
import { ActiveSession, canManageSessions, formatSessionTime, SessionUser } from "./session-management.mts";
import { canDecideRelease, canRequestRelease, CatalogItem, CsrfToken, mutationHeaders, ReleaseDraft, selectableCatalogItems, validateReleaseDraft } from "./control-api.mts";

type Step = { index: number; weight: number; status: string };
type LiveState = { releaseStatus: string; steps: Step[] };
type Evidence = {
  metric_key: string;
  verdict: string;
  reason_code: string;
  baseline_value: number | null;
  canary_value: number | null;
  threshold: number;
  query_template_id: string;
  canary_query_hash: string;
  route?: string | null;
  importance?: "STANDARD" | "CRITICAL";
};
type Analysis = {
  id: string;
  stepIndex: number;
  status: string;
  attempts: number;
  verdict: string | null;
  reasonCode: string | null;
  evidence: Evidence[];
};
type Release = {
  id: string;
  version: string;
  status: string;
  changeSummary: string;
  commitSha: string;
  pipelineUrl: string;
};
type AuthenticationProviders = { oidc: boolean; loginUrl: string | null };
type SessionResponse = { user: SessionUser; csrfToken: string; expiresAt: string };

const demoSteps: Step[] = [
  { index: 0, weight: 10, status: "PASSED" },
  { index: 1, weight: 30, status: "PASSED" },
  { index: 2, weight: 60, status: "EVALUATING" },
  { index: 3, weight: 100, status: "PENDING" },
];
const demoEvidence: Evidence[] = [
  { metric_key: "HTTP_5XX_RATE", verdict: "PASS", reason_code: "ALL_RULES_PASSED", baseline_value: 0.0018, canary_value: 0.0021, threshold: 0.01, query_template_id: "otel-http-server-v1:HTTP_5XX_RATE", canary_query_hash: "2771e562ab326d103068d983a8bdba57" },
  { metric_key: "HTTP_P95_LATENCY_MS", verdict: "PASS", reason_code: "ALL_RULES_PASSED", baseline_value: 251, canary_value: 284, threshold: 500, query_template_id: "otel-http-server-v1:HTTP_P95_LATENCY_MS", canary_query_hash: "840b9a38a30f7eca53a108968ad1f0de" },
  { metric_key: "REQUEST_COUNT", verdict: "PASS", reason_code: "ALL_RULES_PASSED", baseline_value: null, canary_value: 1842, threshold: 1000, query_template_id: "otel-http-server-v1:REQUEST_COUNT", canary_query_hash: "2ca7105ec53b7e222a1b7e4f665fe984" },
  { metric_key: "HTTP_5XX_RATE", verdict: "PASS", reason_code: "ALL_RULES_PASSED", baseline_value: 0.0008, canary_value: 0.0012, threshold: 0.005, query_template_id: "otel-http-server-v1:HTTP_5XX_RATE_BY_ROUTE", canary_query_hash: "91ab6c9272af816713b9722213f45e10", route: "/checkout/{id}", importance: "CRITICAL" },
];
const grafanaUrl = process.env.NEXT_PUBLIC_GRAFANA_URL;
const emptyReleaseDraft: ReleaseDraft = { serviceId: "", environmentId: "", version: "", imageRepository: "", imageDigest: "", changeSummary: "", commitSha: "", pipelineUrl: "", requestedPolicyVersionId: "" };

export default function Home() {
  const [releaseId, setReleaseId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [release, setRelease] = useState<Release | null>(null);
  const [live, setLive] = useState<LiveState>({ releaseStatus: "ANALYZING", steps: demoSteps });
  const [analyses, setAnalyses] = useState<Analysis[]>([{ id: "demo", stepIndex: 2, status: "COMPLETED", attempts: 1, verdict: "PASS", reasonCode: "ALL_RULES_PASSED", evidence: demoEvidence }]);
  const [connection, setConnection] = useState("DEMO SNAPSHOT");
  const [error, setError] = useState("");
  const [canOperate, setCanOperate] = useState(false);
  const [sessionUser, setSessionUser] = useState<SessionUser | null>(null);
  const [activeSessions, setActiveSessions] = useState<ActiveSession[]>([]);
  const [sessionBusy, setSessionBusy] = useState(false);
  const [sessionNotice, setSessionNotice] = useState("");
  const [operationBusy, setOperationBusy] = useState(false);
  const [operationNotice, setOperationNotice] = useState("");
  const [releaseDraft, setReleaseDraft] = useState<ReleaseDraft>(emptyReleaseDraft);
  const [projectId, setProjectId] = useState("");
  const [projects, setProjects] = useState<CatalogItem[]>([]);
  const [services, setServices] = useState<CatalogItem[]>([]);
  const [environments, setEnvironments] = useState<CatalogItem[]>([]);
  const [catalogNotice, setCatalogNotice] = useState("");
  const [requestBusy, setRequestBusy] = useState(false);
  const [requestNotice, setRequestNotice] = useState("");
  const operationInFlight = useRef(false);
  const [authenticationProviders, setAuthenticationProviders] = useState<AuthenticationProviders>({ oidc: false, loginUrl: null });

  useEffect(() => {
    fetch("/control-api/session/providers")
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then(setAuthenticationProviders)
      .catch(() => setAuthenticationProviders({ oidc: false, loginUrl: null }));
    fetch("/control-api/session", { credentials: "include" })
      .then((response) => response.ok ? response.json() as Promise<SessionResponse> : Promise.reject())
      .then((current) => {
        setSessionUser(current.user);
        setCanOperate(current.user.roles.includes("OPERATOR"));
        if (!current.user.demo) void refreshSessions().catch(() => setSessionNotice("활성 세션을 불러올 수 없습니다."));
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!canRequestRelease(sessionUser?.roles ?? [])) return;
    fetch("/control-api/projects?limit=100", { credentials: "include" })
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then((page: { items: CatalogItem[] }) => setProjects(selectableCatalogItems(page.items)))
      .catch(() => setCatalogNotice("접근 가능한 프로젝트를 불러올 수 없습니다."));
  }, [sessionUser]);

  useEffect(() => {
    if (!projectId) return;
    const controller = new AbortController();
    fetch(`/control-api/projects/${projectId}/services?limit=100`, { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then((page: { items: CatalogItem[] }) => setServices(selectableCatalogItems(page.items)))
      .catch((failure: Error) => { if (failure.name !== "AbortError") setCatalogNotice("프로젝트의 서비스를 불러올 수 없습니다."); });
    return () => controller.abort();
  }, [projectId]);

  useEffect(() => {
    if (!releaseDraft.serviceId) return;
    const controller = new AbortController();
    fetch(`/control-api/services/${releaseDraft.serviceId}/environments?limit=100`, { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then((page: { items: CatalogItem[] }) => setEnvironments(selectableCatalogItems(page.items, ["ACTIVE", "ACTIVE_WITH_WARNINGS"])))
      .catch((failure: Error) => { if (failure.name !== "AbortError") setCatalogNotice("서비스의 환경을 불러올 수 없습니다."); });
    return () => controller.abort();
  }, [releaseDraft.serviceId]);

  async function startDemo() {
    const csrf = await fetch("/control-api/session/csrf", { credentials: "include" }).then((response) => response.json());
    const response = await fetch("/control-api/session/demo", { method: "POST", credentials: "include", headers: { [csrf.headerName]: csrf.token } });
    if (!response.ok) { setError("공개 데모 세션을 시작할 수 없습니다."); return; }
    const session = await response.json() as SessionResponse;
    setSessionUser(session.user);
    setActiveSessions([]);
    setCanOperate(session.user.roles.includes("OPERATOR"));
    setConnection("DEMO · VIEW ONLY");
  }

  async function csrfToken() {
    const response = await fetch("/control-api/session/csrf", { credentials: "include" });
    if (!response.ok) throw new Error("보안 토큰을 갱신할 수 없습니다.");
    return response.json() as Promise<CsrfToken>;
  }

  async function refreshSessions() {
    const response = await fetch("/control-api/session/active", { credentials: "include" });
    if (!response.ok) throw new Error("활성 세션을 불러올 수 없습니다.");
    const body = await response.json() as { items: ActiveSession[] };
    setActiveSessions(body.items);
  }

  async function revokeSession(session: ActiveSession) {
    if (!window.confirm(session.current ? "현재 세션을 종료하시겠습니까?" : "선택한 세션을 종료하시겠습니까?")) return;
    setSessionBusy(true);
    setSessionNotice("");
    try {
      const response = await fetch(`/control-api/session/active/${session.reference}`, {
        method: "DELETE", credentials: "include", headers: mutationHeaders(await csrfToken()),
      });
      if (!response.ok) throw new Error("세션 종료 요청이 거부되었습니다.");
      if (session.current) {
        setSessionUser(null);
        setActiveSessions([]);
        setCanOperate(false);
        setSessionNotice("현재 세션이 종료되었습니다.");
      } else {
        await refreshSessions();
        setSessionNotice("선택한 세션을 종료했습니다.");
      }
    } catch (failure) {
      setSessionNotice((failure as Error).message);
    } finally {
      setSessionBusy(false);
    }
  }

  async function revokeOtherSessions() {
    if (!window.confirm("현재 세션을 제외한 모든 세션을 종료하시겠습니까?")) return;
    setSessionBusy(true);
    setSessionNotice("");
    try {
      const response = await fetch("/control-api/session/revoke-others", {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()),
      });
      if (!response.ok) throw new Error("다른 세션 종료 요청이 거부되었습니다.");
      const result = await response.json() as { revoked: number };
      await refreshSessions();
      setSessionNotice(`${result.revoked}개의 다른 세션을 종료했습니다.`);
    } catch (failure) {
      setSessionNotice((failure as Error).message);
    } finally {
      setSessionBusy(false);
    }
  }

  async function load(id: string) {
    setError("");
    const [releaseResponse, analysesResponse] = await Promise.all([
      fetch(`/control-api/releases/${id}`, { credentials: "include" }),
      fetch(`/control-api/releases/${id}/analyses`, { credentials: "include" }),
    ]);
    if (!releaseResponse.ok || !analysesResponse.ok) throw new Error("릴리스 조회 권한 또는 ID를 확인하세요.");
    const loadedRelease = await releaseResponse.json() as Release;
    setRelease(loadedRelease);
    setLive((current) => ({ ...current, releaseStatus: loadedRelease.status }));
    setAnalyses(await analysesResponse.json());
    setActiveId(id);
  }

  useEffect(() => {
    if (!activeId) return;
    const events = new EventSource(`/control-api/releases/${activeId}/events`, { withCredentials: true });
    events.addEventListener("release-state", async (event) => {
      setLive(JSON.parse((event as MessageEvent).data));
      setConnection("LIVE");
      const response = await fetch(`/control-api/releases/${activeId}/analyses`, { credentials: "include" });
      if (response.ok) setAnalyses(await response.json());
    });
    events.onerror = () => setConnection("RECONNECTING");
    return () => events.close();
  }, [activeId]);

  const latest = analyses.at(-1);
  const evidence = latest?.evidence ?? demoEvidence;
  const title = release ? `release · ${release.version}` : "checkout · v1.4.2";
  const activeStep = useMemo(() => live.steps.find((step) => ["RUNNING", "EVALUATING"].includes(step.status)), [live.steps]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    try { await load(releaseId.trim()); } catch (failure) { setError((failure as Error).message); }
  }

  function updateDraft(field: keyof ReleaseDraft, value: string) {
    setReleaseDraft((current) => ({ ...current, [field]: value }));
  }

  function selectProject(value: string) {
    setCatalogNotice(""); setProjectId(value); setServices([]); setEnvironments([]);
    setReleaseDraft((current) => ({ ...current, serviceId: "", environmentId: "" }));
  }

  function selectService(value: string) {
    setCatalogNotice(""); setEnvironments([]);
    setReleaseDraft((current) => ({ ...current, serviceId: value, environmentId: "" }));
  }

  async function requestRelease(event: FormEvent) {
    event.preventDefault();
    if (requestBusy || !canRequestRelease(sessionUser?.roles ?? [])) return;
    const validation = validateReleaseDraft(releaseDraft);
    if (validation) { setError(validation); return; }
    setRequestBusy(true);
    setRequestNotice("");
    setError("");
    try {
      const response = await fetch("/control-api/releases", {
        method: "POST", credentials: "include",
        headers: mutationHeaders(await csrfToken(), { idempotencyKey: crypto.randomUUID() + crypto.randomUUID(), json: true }),
        body: JSON.stringify({
          ...releaseDraft,
          serviceId: releaseDraft.serviceId.trim(), environmentId: releaseDraft.environmentId.trim(),
          version: releaseDraft.version.trim(), imageRepository: releaseDraft.imageRepository.trim(),
          changeSummary: releaseDraft.changeSummary.trim(), commitSha: releaseDraft.commitSha.trim(),
          pipelineUrl: releaseDraft.pipelineUrl.trim(),
          requestedPolicyVersionId: releaseDraft.requestedPolicyVersionId.trim() || null,
        }),
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null;
        throw new Error(problem?.detail ?? "릴리스 요청이 거부되었습니다.");
      }
      const created = await response.json() as { id: string };
      setReleaseId(created.id);
      setRequestNotice(`릴리스 ${created.id} 요청이 생성되었습니다.`);
      await load(created.id);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setRequestBusy(false);
    }
  }

  async function operate(action: "promote" | "pause" | "resume" | "abort") {
    if (!activeId || operationInFlight.current) return;
    const reason = window.prompt(`${action} 조작 사유를 입력하세요.`)?.trim();
    if (reason === undefined) return;
    if (reason.length === 0 || reason.length > 1000) {
      setError("조작 사유는 1자 이상 1000자 이하여야 합니다.");
      return;
    }
    operationInFlight.current = true;
    setOperationBusy(true);
    setOperationNotice("");
    setError("");
    try {
      const idempotencyKey = crypto.randomUUID() + crypto.randomUUID();
      const response = await fetch(`/control-api/releases/${activeId}/${action}`, {
        method: "POST", credentials: "include",
        headers: mutationHeaders(await csrfToken(), { idempotencyKey, json: true }),
        body: JSON.stringify({ reason }),
      });
      if (!response.ok) throw new Error(`${action} 요청이 거부되었습니다.`);
      setOperationNotice(`${action} 요청이 접수되었습니다.`);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      operationInFlight.current = false;
      setOperationBusy(false);
    }
  }

  async function decide(action: "approve" | "reject") {
    if (!activeId || operationInFlight.current) return;
    const reason = window.prompt(`${action === "approve" ? "승인" : "거부"} 사유를 입력하세요.`)?.trim();
    if (reason === undefined) return;
    if (reason.length === 0 || reason.length > 1000) {
      setError("결정 사유는 1자 이상 1000자 이하여야 합니다.");
      return;
    }
    operationInFlight.current = true;
    setOperationBusy(true);
    setOperationNotice("");
    setError("");
    try {
      const idempotencyKey = crypto.randomUUID() + crypto.randomUUID();
      const response = await fetch(`/control-api/releases/${activeId}/${action}`, {
        method: "POST", credentials: "include",
        headers: mutationHeaders(await csrfToken(), { idempotencyKey, json: true }),
        body: JSON.stringify({ reason }),
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null;
        if (problem?.code === "SELF_APPROVAL_NOT_ALLOWED") throw new Error("요청자는 자신의 릴리스를 승인할 수 없습니다.");
        throw new Error(problem?.detail ?? `${action} 요청이 거부되었습니다.`);
      }
      const decided = await response.json() as { status: string };
      setRelease((current) => current ? { ...current, status: decided.status } : current);
      setLive((current) => ({ ...current, releaseStatus: decided.status }));
      setOperationNotice(`${action === "approve" ? "승인" : "거부"} 결정이 기록되었습니다.`);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      operationInFlight.current = false;
      setOperationBusy(false);
    }
  }

  return (
    <main className={styles.page}>
      <nav className={styles.nav}>
        <span className={styles.brand}><span className={styles.brandMark}>RP</span>ReleasePilot</span>
        <span className={styles.live}><i />{connection}{authenticationProviders.oidc && authenticationProviders.loginUrl && <a href={authenticationProviders.loginUrl}>조직 SSO</a>}<button onClick={startDemo}>읽기 전용 데모</button></span>
      </nav>
      <section className={styles.shell}>
        <header className={styles.topline}>
          <div><p>RELEASE OPERATIONS</p><h1>Progressive delivery control room</h1><span>Canary와 Blue/Green의 판정 근거부터 실행 결과까지 한 화면에서 추적합니다.</span></div>
          <form onSubmit={submit}><input aria-label="Release ID" placeholder="Release UUID" value={releaseId} onChange={(event) => setReleaseId(event.target.value)} /><button>불러오기</button></form>
        </header>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {canRequestRelease(sessionUser?.roles ?? []) && <details className={sessionStyles.releaseRequest}>
          <summary>NEW RELEASE REQUEST <span>Developer workflow</span></summary>
          <form onSubmit={(event) => void requestRelease(event)}>
            <label>Project<select required value={projectId} onChange={(event) => selectProject(event.target.value)}><option value="">{projects.length ? "프로젝트 선택" : "활성 프로젝트 없음"}</option>{projects.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.key})</option>)}</select></label>
            <label>Service<select required disabled={!projectId} value={releaseDraft.serviceId} onChange={(event) => selectService(event.target.value)}><option value="">{projectId && !services.length ? "활성 서비스 없음" : "서비스 선택"}</option>{services.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.key})</option>)}</select></label>
            <label>Environment<select required disabled={!releaseDraft.serviceId} value={releaseDraft.environmentId} onChange={(event) => updateDraft("environmentId", event.target.value)}><option value="">{releaseDraft.serviceId && !environments.length ? "검증된 환경 없음" : "검증된 환경 선택"}</option>{environments.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.strategy}</option>)}</select></label>
            <label>Version<input required maxLength={100} value={releaseDraft.version} onChange={(event) => updateDraft("version", event.target.value)} placeholder="v1.2.3" /></label>
            <label>Image repository<input required maxLength={500} value={releaseDraft.imageRepository} onChange={(event) => updateDraft("imageRepository", event.target.value)} placeholder="registry.example/team/app" /></label>
            <label className={sessionStyles.wide}>Image digest<input required pattern="sha256:[a-f0-9]{64}" value={releaseDraft.imageDigest} onChange={(event) => updateDraft("imageDigest", event.target.value)} placeholder="sha256:…" /></label>
            <label className={sessionStyles.wide}>Change summary<textarea required maxLength={2000} value={releaseDraft.changeSummary} onChange={(event) => updateDraft("changeSummary", event.target.value)} /></label>
            <label>Commit SHA<input required pattern="[a-fA-F0-9]{40}" value={releaseDraft.commitSha} onChange={(event) => updateDraft("commitSha", event.target.value)} /></label>
            <label>Pipeline URL<input required type="url" maxLength={1000} value={releaseDraft.pipelineUrl} onChange={(event) => updateDraft("pipelineUrl", event.target.value)} /></label>
            <label className={sessionStyles.wide}>Policy Version ID <small>선택 사항 · 비우면 Environment 기본 정책</small><input value={releaseDraft.requestedPolicyVersionId} onChange={(event) => updateDraft("requestedPolicyVersionId", event.target.value)} placeholder="UUID" /></label>
            <button disabled={requestBusy}>{requestBusy ? "요청 중…" : "릴리스 요청"}</button>
          </form>
          {catalogNotice && <p role="alert">{catalogNotice}</p>}
          {requestNotice && <p role="status">{requestNotice}</p>}
        </details>}
        <section className={styles.grid}>
          <article className={styles.releaseCard}>
            <header><div><span>PRODUCTION RELEASE</span><h2>{title}</h2></div><b data-status={live.releaseStatus}>{live.releaseStatus}</b></header>
            <div className={styles.meta}><span>현재 단계</span><strong>{activeStep ? `${activeStep.weight}%` : "—"}</strong><span>최근 판정</span><strong>{latest?.verdict ?? "관찰 중"}</strong></div>
            <div className={styles.stages}>{live.steps.map((step) => <div className={styles.stage} data-status={step.status} key={step.index}><i /><span>{step.weight}%</span><small>{step.status}</small></div>)}</div>
            <footer><button onClick={() => operate("promote")} disabled={!activeId || !canOperate || operationBusy}>Promote</button><button onClick={() => operate("pause")} disabled={!activeId || !canOperate || operationBusy}>Pause</button><button onClick={() => operate("resume")} disabled={!activeId || !canOperate || operationBusy}>Resume</button><button className={styles.danger} onClick={() => operate("abort")} disabled={!activeId || !canOperate || operationBusy}>Abort</button>{grafanaUrl && <a href={grafanaUrl} target="_blank" rel="noreferrer">Grafana에서 조사 ↗</a>}</footer>
            {canDecideRelease(sessionUser?.roles ?? [], release?.status) && <div className={sessionStyles.approvalActions}><span>APPROVAL REQUIRED</span><button onClick={() => void decide("approve")} disabled={operationBusy}>Approve</button><button className={styles.danger} onClick={() => void decide("reject")} disabled={operationBusy}>Reject</button></div>}
            {operationNotice && <p className={sessionStyles.operationNotice} role="status">{operationNotice}</p>}
          </article>
          <aside className={styles.activity}><p>ANALYSIS JOB</p><strong>{latest?.status ?? "EVALUATING"}</strong><dl><div><dt>Attempt</dt><dd>{latest?.attempts ?? 1}</dd></div><div><dt>Verdict</dt><dd>{latest?.verdict ?? "—"}</dd></div><div><dt>Reason</dt><dd>{latest?.reasonCode ?? "관찰 시간 진행 중"}</dd></div></dl><code>{activeId ?? "demo-correlation · 9f31c8"}</code></aside>
        </section>
        <section className={styles.evidence}><header><div><p>DECISION EVIDENCE</p><h2>같은 시간창의 stable / canary 비교</h2></div><span>Route 범위와 Query hash로 재현 가능</span></header><div className={styles.table}><div className={styles.rowHead}><span>Metric / Route</span><span>Stable</span><span>Canary</span><span>Threshold</span><span>Result</span></div>{evidence.map((item) => <div className={styles.row} key={`${item.metric_key}:${item.route ?? "global"}`}><span><strong>{item.metric_key}</strong>{item.route && <small className={styles.route}>{item.importance ?? "STANDARD"} · {item.route}</small>}<small>{item.canary_query_hash.slice(0, 12)}…</small></span><span>{format(item.baseline_value, item.metric_key)}</span><span>{format(item.canary_value, item.metric_key)}</span><span>{format(item.threshold, item.metric_key)}</span><b data-verdict={item.verdict}>{item.verdict}</b></div>)}</div></section>
        <section className={sessionStyles.sessions} aria-labelledby="sessions-title">
          <header><div><p>ACCOUNT SECURITY</p><h2 id="sessions-title">활성 세션</h2></div>{canManageSessions(sessionUser) && <button onClick={() => void revokeOtherSessions()} disabled={sessionBusy || activeSessions.length < 2}>다른 세션 모두 종료</button>}</header>
          {!sessionUser && <p className={sessionStyles.sessionEmpty}>조직 SSO로 로그인하면 활성 세션을 확인하고 원격으로 종료할 수 있습니다.</p>}
          {sessionUser?.demo && <p className={sessionStyles.sessionEmpty}>공유 데모에서는 다른 방문자의 연결을 보호하기 위해 세션 관리가 비활성화됩니다.</p>}
          {canManageSessions(sessionUser) && <div className={sessionStyles.sessionList}>{activeSessions.map((item) => <article key={item.reference}><div><strong>{item.current ? "현재 세션" : "활성 세션"}</strong><code>{item.reference}</code><small>최근 사용 {formatSessionTime(item.lastAccessedAt)} · 만료 {formatSessionTime(item.expiresAt)}</small></div><button onClick={() => void revokeSession(item)} disabled={sessionBusy}>{item.current ? "로그아웃" : "종료"}</button></article>)}</div>}
          {sessionNotice && <p className={sessionStyles.sessionNotice} role="status">{sessionNotice}</p>}
        </section>
      </section>
    </main>
  );
}

function format(value: number | null, key: string) {
  if (value === null) return "—";
  if (key === "HTTP_5XX_RATE") return `${(value * 100).toFixed(2)}%`;
  if (key === "HTTP_P95_LATENCY_MS") return `${Math.round(value)}ms`;
  return Math.round(value).toLocaleString();
}
