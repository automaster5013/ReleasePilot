"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import styles from "./page.module.css";
import sessionStyles from "./session.module.css";
import browserStyles from "./release-browser.module.css";
import auditStyles from "./audit-timeline.module.css";
import { ActiveSession, canManageSessions, formatSessionTime, sessionConnectionLabel, SessionUser } from "./session-management.mts";
import { clusterConnectionDraftIssue, createLatestRequestGuard, createMutationGate, approvalReadinessLabel, approvalReadinessMessage, AuditChainVerification, AuditEventView, auditEventLabel, auditIntegrityLabel, canDecideRelease, canManageConnections, canRequestRelease, canRevalidateEnvironment, canVerifyAudit, CatalogItem, ClusterConnection, ClusterConnectionDraft, ConnectionFilter, connectionAuditDetail, connectionValidationLabel, CsrfToken, EnvironmentValidation, readinessMutationHeaders, environmentAllowsRelease, environmentValidationSummary, filterConnections, mutationHeaders, parseNamespaces, PrometheusConnection, PrometheusConnectionDraft, prometheusConnectionDraftIssue, ReleaseDraft, releaseDraftIssue, releaseOptionLabel, releaseRequestReadinessMessage, ReleaseSummary, selectableCatalogItems, validateClusterConnectionDraft, validatePrometheusConnectionDraft } from "./control-api.mts";

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

function restoreFocusAfterRender(element: HTMLElement | null) {
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      if (element?.isConnected && !(element instanceof HTMLButtonElement && element.disabled)) element.focus();
    });
  });
}
type PolicyStep = { weight: number; minimumObservationSeconds: number };
type PolicyMetric = { key: string; threshold: number; comparison?: string; route?: string; importance?: string };
type Release = {
  id: string;
  environmentId: string;
  version: string;
  imageRepository: string;
  imageDigest: string;
  status: string;
  changeSummary: string;
  commitSha: string;
  pipelineUrl: string;
  createdAt: string;
  context: { serviceName: string; environmentName: string };
  requester: { displayName: string; username: string };
  policySnapshot: { name: string; checksum: string; definition: { strategy: string; steps: PolicyStep[]; metrics: PolicyMetric[] } };
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
const emptyClusterDraft: ClusterConnectionDraft = { name: "", apiServer: "", namespaces: "", secretRef: "" };
const emptyPrometheusDraft: PrometheusConnectionDraft = { name: "", baseUrl: "", secretRef: "", queryTimeoutSeconds: "15" };

export default function Home() {
  const [releaseId, setReleaseId] = useState("");
  const [recentReleases, setRecentReleases] = useState<ReleaseSummary[]>([]);
  const [releaseListBusy, setReleaseListBusy] = useState(false);
  const releaseListInFlight = useRef(false);
  const [releaseLoadBusy, setReleaseLoadBusy] = useState(false);
  const releaseLoadInFlight = useRef(false);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [release, setRelease] = useState<Release | null>(null);
  const [live, setLive] = useState<LiveState>({ releaseStatus: "ANALYZING", steps: demoSteps });
  const [analyses, setAnalyses] = useState<Analysis[]>([{ id: "demo", stepIndex: 2, status: "COMPLETED", attempts: 1, verdict: "PASS", reasonCode: "ALL_RULES_PASSED", evidence: demoEvidence }]);
  const [auditEvents, setAuditEvents] = useState<AuditEventView[]>([]);
  const [auditIntegrity, setAuditIntegrity] = useState<AuditChainVerification | null>(null);
  const [auditIntegrityBusy, setAuditIntegrityBusy] = useState(false);
  const auditIntegrityInFlight = useRef(false);
  const [connection, setConnection] = useState("DEMO SNAPSHOT");
  const [error, setError] = useState("");
  const [canOperate, setCanOperate] = useState(false);
  const [sessionUser, setSessionUser] = useState<SessionUser | null>(null);
  const [demoBusy, setDemoBusy] = useState(false);
  const demoInFlight = useRef(false);
  const [logoutBusy, setLogoutBusy] = useState(false);
  const logoutInFlight = useRef(false);
  const [activeSessions, setActiveSessions] = useState<ActiveSession[]>([]);
  const [sessionBusy, setSessionBusy] = useState(false);
  const sessionMutationInFlight = useRef(false);
  const [sessionNotice, setSessionNotice] = useState("");
  const [operationBusy, setOperationBusy] = useState(false);
  const [operationNotice, setOperationNotice] = useState("");
  const [releaseDraft, setReleaseDraft] = useState<ReleaseDraft>(emptyReleaseDraft);
  const [releaseValidationIssue, setReleaseValidationIssue] = useState<ReturnType<typeof releaseDraftIssue>>(null);
  const [projectId, setProjectId] = useState("");
  const [projects, setProjects] = useState<CatalogItem[]>([]);
  const [services, setServices] = useState<CatalogItem[]>([]);
  const [environments, setEnvironments] = useState<CatalogItem[]>([]);
  const [environmentValidation, setEnvironmentValidation] = useState<EnvironmentValidation | null>(null);
  const selectedEnvironmentId = useRef("");
  const environmentSelectionGeneration = useRef(0);
  const latestEnvironmentValidation = useRef(createLatestRequestGuard());
  const [environmentValidationNotice, setEnvironmentValidationNotice] = useState("");
  const [environmentValidationBusy, setEnvironmentValidationBusy] = useState(false);
  const [environmentAuditEvents, setEnvironmentAuditEvents] = useState<AuditEventView[]>([]);
  const [approvalEnvironmentValidation, setApprovalEnvironmentValidation] = useState<EnvironmentValidation | null>(null);
  const [approvalReadinessNotice, setApprovalReadinessNotice] = useState("");
  const [catalogNotice, setCatalogNotice] = useState("");
  const [requestBusy, setRequestBusy] = useState(false);
  const requestInFlight = useRef(createMutationGate());
  const latestReleaseLoad = useRef(createLatestRequestGuard());
  const [requestNotice, setRequestNotice] = useState("");
  const operationInFlight = useRef(false);
  const [authenticationProviders, setAuthenticationProviders] = useState<AuthenticationProviders>({ oidc: false, loginUrl: null });
  const [prometheusConnections, setPrometheusConnections] = useState<PrometheusConnection[]>([]);
  const [clusterConnections, setClusterConnections] = useState<ClusterConnection[]>([]);
  const [connectionBusyId, setConnectionBusyId] = useState("");
  const connectionRefreshInFlight = useRef(false);
  const connectionValidationInFlight = useRef(false);
  const [connectionNotice, setConnectionNotice] = useState("");
  const [connectionActionError, setConnectionActionError] = useState("");
  const [clusterDraft, setClusterDraft] = useState<ClusterConnectionDraft>(emptyClusterDraft);
  const [prometheusDraft, setPrometheusDraft] = useState<PrometheusConnectionDraft>(emptyPrometheusDraft);
  const [connectionValidationIssue, setConnectionValidationIssue] = useState<{ kind: "cluster"; field: keyof ClusterConnectionDraft; message: string } | { kind: "prometheus"; field: keyof PrometheusConnectionDraft; message: string } | null>(null);
  const [connectionCreateBusy, setConnectionCreateBusy] = useState(false);
  const [connectionAuditId, setConnectionAuditId] = useState("");
  const [connectionAuditEvents, setConnectionAuditEvents] = useState<AuditEventView[]>([]);
  const [connectionAuditBusy, setConnectionAuditBusy] = useState(false);
  const [clusterQuery, setClusterQuery] = useState("");
  const [clusterFilter, setClusterFilter] = useState<ConnectionFilter>("ALL");
  const [prometheusQuery, setPrometheusQuery] = useState("");
  const [prometheusFilter, setPrometheusFilter] = useState<ConnectionFilter>("ALL");
  const [connectionClock, setConnectionClock] = useState(() => Date.now());
  const filteredClusterConnections = useMemo(() => filterConnections(clusterConnections, clusterQuery, clusterFilter, connectionClock), [clusterConnections, clusterQuery, clusterFilter, connectionClock]);
  const filteredPrometheusConnections = useMemo(() => filterConnections(prometheusConnections, prometheusQuery, prometheusFilter, connectionClock), [prometheusConnections, prometheusQuery, prometheusFilter, connectionClock]);

  useEffect(() => {
    if (!sessionUser) return;
    const tick = () => setConnectionClock(Date.now());
    const timer = window.setInterval(tick, 30_000);
    window.addEventListener("focus", tick);
    document.addEventListener("visibilitychange", tick);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", tick); document.removeEventListener("visibilitychange", tick); };
  }, [sessionUser]);

  const refreshAuditIntegrity = useCallback(async (trigger: HTMLElement | null = null) => {
    if (auditIntegrityInFlight.current) return;
    auditIntegrityInFlight.current = true;
    if (trigger) setError("");
    setAuditIntegrityBusy(true);
    try {
      const response = await fetch("/control-api/audit-events/verify", { credentials: "include" });
      if (!response.ok) throw new Error();
      setAuditIntegrity(await response.json() as AuditChainVerification);
    } catch {
      setAuditIntegrity(null);
      setError("감사 체인을 검증할 수 없습니다.");
      if (trigger) restoreFocusAfterRender(trigger);
    } finally {
      auditIntegrityInFlight.current = false;
      setAuditIntegrityBusy(false);
    }
  }, []);

  const refreshEnvironmentAudit = useCallback(async (id: string) => {
    const selection = environmentSelectionGeneration.current;
    const response = await fetch(`/control-api/audit-events?aggregateType=ENVIRONMENT&aggregateId=${id}`, { credentials: "include" });
    if (response.ok) {
      const page = await response.json() as { items: AuditEventView[] };
      if (selectedEnvironmentId.current === id && selection === environmentSelectionGeneration.current) setEnvironmentAuditEvents(page.items);
    }
  }, []);

  const refreshReleases = useCallback(async (trigger: HTMLElement | null = null) => {
    if (releaseListInFlight.current) return;
    releaseListInFlight.current = true;
    if (trigger) setError("");
    setReleaseListBusy(true);
    try {
      const response = await fetch("/control-api/releases?limit=20", { credentials: "include" });
      if (!response.ok) throw new Error();
      const page = await response.json() as { items: ReleaseSummary[] };
      setRecentReleases(page.items);
      setReleaseId((current) => current || page.items[0]?.id || "");
    } catch {
      setError("최근 릴리스를 불러올 수 없습니다.");
      if (trigger) restoreFocusAfterRender(trigger);
    } finally {
      releaseListInFlight.current = false;
      setReleaseListBusy(false);
    }
  }, []);

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
        void refreshReleases();
        if (!current.user.demo) void refreshSessions().catch(() => setSessionNotice("활성 세션을 불러올 수 없습니다."));
      })
      .catch(() => undefined);
  }, [refreshReleases]);

  useEffect(() => {
    if (!canVerifyAudit(sessionUser?.roles ?? [])) return;
    const controller = new AbortController();
    fetch("/control-api/audit-events/verify", { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<AuditChainVerification> : Promise.reject())
      .then(setAuditIntegrity)
      .catch((failure: Error) => { if (failure.name !== "AbortError") setAuditIntegrity(null); });
    return () => controller.abort();
  }, [sessionUser]);

  const refreshPrometheusConnections = useCallback(async () => {
    const response = await fetch("/control-api/connections/prometheus", { credentials: "include" });
    if (!response.ok) throw new Error("Prometheus 연결 목록을 불러올 수 없습니다.");
    setPrometheusConnections(await response.json() as PrometheusConnection[]);
  }, []);

  const refreshClusterConnections = useCallback(async () => {
    const response = await fetch("/control-api/connections/clusters", { credentials: "include" });
    if (!response.ok) throw new Error("Kubernetes 연결 목록을 불러올 수 없습니다.");
    setClusterConnections(await response.json() as ClusterConnection[]);
  }, []);

  useEffect(() => {
    if (!canManageConnections(sessionUser?.roles ?? [])) return;
    const controller = new AbortController();
    fetch("/control-api/connections/prometheus", { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<PrometheusConnection[]> : Promise.reject())
      .then(setPrometheusConnections)
      .catch((failure: Error) => { if (failure.name !== "AbortError") setConnectionNotice("Prometheus 연결 목록을 불러올 수 없습니다."); });
    return () => controller.abort();
  }, [sessionUser]);

  useEffect(() => {
    if (!canManageConnections(sessionUser?.roles ?? [])) return;
    const controller = new AbortController();
    fetch("/control-api/connections/clusters", { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<ClusterConnection[]> : Promise.reject())
      .then(setClusterConnections)
      .catch((failure: Error) => { if (failure.name !== "AbortError") setConnectionNotice("Kubernetes 연결 목록을 불러올 수 없습니다."); });
    return () => controller.abort();
  }, [sessionUser]);

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

  useEffect(() => {
    if (!releaseDraft.environmentId) return;
    const isCurrent = latestEnvironmentValidation.current.begin();
    const controller = new AbortController();
    fetch(`/control-api/environments/${releaseDraft.environmentId}/validation-results/latest`, { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<EnvironmentValidation> : Promise.reject())
      .then((result) => { if (isCurrent() && !controller.signal.aborted && result.environmentId === selectedEnvironmentId.current) setEnvironmentValidation(result); })
      .catch((failure: Error) => { if (isCurrent() && !controller.signal.aborted && failure.name !== "AbortError") setEnvironmentValidationNotice("최신 환경 점검 결과를 불러올 수 없습니다."); });
    return () => controller.abort();
  }, [releaseDraft.environmentId]);

  useEffect(() => {
    if (!releaseDraft.environmentId || !canRevalidateEnvironment(sessionUser?.roles ?? [])) return;
    const controller = new AbortController();
    fetch(`/control-api/audit-events?aggregateType=ENVIRONMENT&aggregateId=${releaseDraft.environmentId}`, { credentials: "include", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<{ items: AuditEventView[] }> : Promise.reject())
      .then((page) => { if (!controller.signal.aborted && selectedEnvironmentId.current === releaseDraft.environmentId) setEnvironmentAuditEvents(page.items); })
      .catch((failure: Error) => { if (!controller.signal.aborted && failure.name !== "AbortError") setEnvironmentAuditEvents([]); });
    return () => controller.abort();
  }, [releaseDraft.environmentId, sessionUser]);

  async function logout(changeAccount = false, trigger: HTMLElement | null = null) {
    if (logoutInFlight.current) return;
    logoutInFlight.current = true;
    setLogoutBusy(true);
    setError("");
    try {
      const response = await fetch("/control-api/session/logout", {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()),
      });
      if (!response.ok) throw new Error("로그아웃하지 못했습니다. 다시 시도해 주세요.");
      // A full document navigation discards authenticated state and pending requests.
      window.location.assign(changeAccount && authenticationProviders.loginUrl ? authenticationProviders.loginUrl : "/");
    } catch (failure) {
      setError((failure as Error).message);
      logoutInFlight.current = false;
      setLogoutBusy(false);
      restoreFocusAfterRender(trigger);
    }
  }

  async function startDemo(trigger: HTMLElement | null = null) {
    if (demoInFlight.current) return;
    demoInFlight.current = true;
    let failed = true;
    setDemoBusy(true);
    setError("");
    try {
    const response = await fetch("/control-api/session/demo", { method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()) });
    if (!response.ok) { setError("공개 데모 세션을 시작할 수 없습니다."); return; }
    const session = await response.json() as SessionResponse;
    setSessionUser(session.user);
    setActiveSessions([]);
    setAuditIntegrity(null);
    setCanOperate(session.user.roles.includes("OPERATOR"));
    await refreshReleases();
    failed = false;
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      demoInFlight.current = false;
      setDemoBusy(false);
      if (failed) restoreFocusAfterRender(trigger);
    }
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

  async function revokeSession(session: ActiveSession, trigger: HTMLElement | null = null) {
    if (sessionMutationInFlight.current) return;
    if (!window.confirm(session.current ? "현재 세션을 종료하시겠습니까?" : "선택한 세션을 종료하시겠습니까?")) return;
    sessionMutationInFlight.current = true;
    setError("");
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
        setAuditIntegrity(null);
        setCanOperate(false);
        setSessionNotice("현재 세션이 종료되었습니다.");
      } else {
        await refreshSessions();
        setSessionNotice("선택한 세션을 종료했습니다.");
      }
    } catch (failure) {
      setError((failure as Error).message);
      restoreFocusAfterRender(trigger);
    } finally {
      sessionMutationInFlight.current = false;
      setSessionBusy(false);
    }
  }

  async function revokeOtherSessions(trigger: HTMLElement | null = null) {
    if (sessionMutationInFlight.current) return;
    if (!window.confirm("현재 세션을 제외한 모든 세션을 종료하시겠습니까?")) return;
    sessionMutationInFlight.current = true;
    setError("");
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
      setError((failure as Error).message);
      restoreFocusAfterRender(trigger);
    } finally {
      sessionMutationInFlight.current = false;
      setSessionBusy(false);
    }
  }

  async function load(id: string) {
    const isCurrent = latestReleaseLoad.current.begin();
    setError("");
    setOperationNotice("");
    setActiveId("");
    setRelease(null);
    setAnalyses([]);
    setAuditEvents([]);
    setApprovalEnvironmentValidation(null);
    setApprovalReadinessNotice("환경 readiness를 확인하는 중…");
    try {
      const [releaseResponse, analysesResponse, auditResponse] = await Promise.all([
        fetch(`/control-api/releases/${id}`, { credentials: "include" }),
        fetch(`/control-api/releases/${id}/analyses`, { credentials: "include" }),
        fetch(`/control-api/audit-events?aggregateType=RELEASE&aggregateId=${id}`, { credentials: "include" }),
      ]);
      if (!isCurrent()) return;
      if (!releaseResponse.ok || !analysesResponse.ok || !auditResponse.ok) throw new Error("릴리스 조회 권한 또는 ID를 확인하세요.");
      const [loadedRelease, loadedAnalyses, loadedAudit] = await Promise.all([
        releaseResponse.json() as Promise<Release>,
        analysesResponse.json(),
        auditResponse.json() as Promise<{ items: AuditEventView[] }>,
      ]);
      if (!isCurrent()) return;
      let validation: EnvironmentValidation | null = null;
      let readinessNotice = "";
      if (loadedRelease.status === "PENDING_APPROVAL") {
        try {
          const response = await fetch(`/control-api/environments/${loadedRelease.environmentId}/validation-results/latest`, { credentials: "include" });
          if (!response.ok) throw new Error();
          validation = await response.json() as EnvironmentValidation;
          if (validation.environmentId !== loadedRelease.environmentId) throw new Error();
        } catch {
          validation = null;
          readinessNotice = "환경 readiness를 확인할 수 없어 승인을 차단했습니다.";
        }
      }
      if (!isCurrent()) return;
      setRelease(loadedRelease);
      setApprovalEnvironmentValidation(validation);
      setApprovalReadinessNotice(readinessNotice);
      setLive((current) => ({ ...current, releaseStatus: loadedRelease.status }));
      setAnalyses(loadedAnalyses);
      setAuditEvents(loadedAudit.items);
      setActiveId(id);
      setRecentReleases((current) => current.map((item) => item.id === id ? { ...item, status: loadedRelease.status } : item));
    } catch (failure) {
      if (isCurrent()) throw failure;
    }
  }

  const refreshAudit = useCallback(async (id: string, isCurrent: () => boolean = () => true) => {
    const response = await fetch(`/control-api/audit-events?aggregateType=RELEASE&aggregateId=${id}`, { credentials: "include" });
    if (response.ok) {
      const page = await response.json() as { items: AuditEventView[] };
      if (isCurrent()) setAuditEvents(page.items);
    }
  }, []);

  useEffect(() => {
    if (!activeId) return;
    let closed = false;
    const events = new EventSource(`/control-api/releases/${activeId}/events`, { withCredentials: true });
    events.addEventListener("release-state", async (event) => {
      if (closed) return;
      setLive(JSON.parse((event as MessageEvent).data));
      setConnection("LIVE");
      const response = await fetch(`/control-api/releases/${activeId}/analyses`, { credentials: "include" });
      if (response.ok) {
        const loaded = await response.json();
        if (!closed) setAnalyses(loaded);
      }
      if (!closed) await refreshAudit(activeId, () => !closed);
    });
    events.onerror = () => { if (!closed) setConnection("RECONNECTING"); };
    return () => { closed = true; events.close(); };
  }, [activeId, refreshAudit]);

  const latest = analyses.at(-1);
  const evidence = latest?.evidence ?? demoEvidence;
  const title = release ? `release · ${release.version}` : "checkout · v1.4.2";
  const activeStep = useMemo(() => live.steps.find((step) => ["RUNNING", "EVALUATING"].includes(step.status)), [live.steps]);
  const passedEvidence = evidence.filter((item) => item.verdict === "PASS").length;
  const progress = activeStep?.weight ?? (live.steps.every((step) => step.status === "PASSED") ? 100 : 0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (releaseLoadInFlight.current) return;
    releaseLoadInFlight.current = true;
    const trigger = (event.nativeEvent as SubmitEvent).submitter as HTMLElement | null;
    setReleaseLoadBusy(true);
    try { await load(releaseId.trim()); } catch (failure) {
      setError((failure as Error).message);
      restoreFocusAfterRender(trigger);
    } finally {
      releaseLoadInFlight.current = false;
      setReleaseLoadBusy(false);
    }
  }

  function updateDraft(field: keyof ReleaseDraft, value: string) {
    setReleaseDraft((current) => ({ ...current, [field]: value }));
    setReleaseValidationIssue((current) => current?.field === field ? null : current);
  }

  function selectProject(value: string) {
    environmentSelectionGeneration.current++;
    latestEnvironmentValidation.current.begin();
    selectedEnvironmentId.current = "";
    setCatalogNotice(""); setProjectId(value); setServices([]); setEnvironments([]);
    setEnvironmentValidation(null); setEnvironmentValidationNotice(""); setEnvironmentAuditEvents([]);
    setReleaseValidationIssue(null);
    setReleaseDraft((current) => ({ ...current, serviceId: "", environmentId: "" }));
  }

  function selectService(value: string) {
    environmentSelectionGeneration.current++;
    latestEnvironmentValidation.current.begin();
    selectedEnvironmentId.current = "";
    setCatalogNotice(""); setEnvironments([]);
    setEnvironmentValidation(null); setEnvironmentValidationNotice(""); setEnvironmentAuditEvents([]);
    setReleaseValidationIssue(null);
    setReleaseDraft((current) => ({ ...current, serviceId: value, environmentId: "" }));
  }

  function selectEnvironment(value: string) {
    environmentSelectionGeneration.current++;
    latestEnvironmentValidation.current.begin();
    selectedEnvironmentId.current = value;
    setEnvironmentValidation(null); setEnvironmentValidationNotice(""); setEnvironmentAuditEvents([]);
    updateDraft("environmentId", value);
  }

  async function revalidateEnvironment(trigger: HTMLElement | null = null) {
    if (!releaseDraft.environmentId || !canRevalidateEnvironment(sessionUser?.roles ?? []) || environmentValidationBusy || requestBusy) return;
    if (!requestInFlight.current.tryAcquire()) return;
    const environmentId = releaseDraft.environmentId;
    const isCurrent = latestEnvironmentValidation.current.begin();
    setError("");
    setEnvironmentValidation(null);
    setEnvironmentValidationBusy(true); setEnvironmentValidationNotice("환경 재검증 중에는 릴리스를 요청할 수 없습니다.");
    try {
      const response = await fetch(`/control-api/environments/${releaseDraft.environmentId}/validate`, { method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()) });
      if (!response.ok) throw new Error("환경 재검증 요청이 거부되었습니다.");
      const result = await response.json() as EnvironmentValidation;
      if (!isCurrent() || selectedEnvironmentId.current !== environmentId) return;
      if (result.environmentId !== environmentId) throw new Error("환경 검증 결과의 대상이 일치하지 않습니다.");
      setEnvironmentValidation(result);
      setEnvironments((current) => current.map((item) => item.id === result.environmentId ? { ...item, status: result.status } : item));
      setEnvironmentValidationNotice(environmentAllowsRelease(result) ? "환경 재검증이 완료되었습니다." : "재검증에 실패한 환경에서는 릴리스를 요청할 수 없습니다.");
      await refreshEnvironmentAudit(result.environmentId);
    } catch (failure) {
      if (isCurrent() && selectedEnvironmentId.current === environmentId) {
        setEnvironmentValidationNotice("");
        setError((failure as Error).message);
        restoreFocusAfterRender(trigger);
      }
    } finally {
      requestInFlight.current.release();
      setEnvironmentValidationBusy(false);
    }
  }

  async function requestRelease(event: FormEvent) {
    event.preventDefault();
    const trigger = (event.nativeEvent as SubmitEvent).submitter as HTMLElement | null;
    if (requestBusy || environmentValidationBusy || !canRequestRelease(sessionUser?.roles ?? [])) return;
    const validation = releaseDraftIssue(releaseDraft);
    if (validation) {
      setReleaseValidationIssue(validation);
      setError(validation.message);
      restoreFocusAfterRender(document.querySelector<HTMLElement>(`[data-release-field="${validation.field}"]`));
      return;
    }
    if (!environmentAllowsRelease(environmentValidation, Date.now(), releaseDraft.environmentId)) {
      setError("환경 검증이 만료되었거나 사용할 수 없습니다. 최신 검증 결과를 확인한 뒤 다시 요청하세요.");
      return;
    }
    if (!requestInFlight.current.tryAcquire()) return;
    setRequestBusy(true);
    setRequestNotice("");
    setReleaseValidationIssue(null);
    setError("");
    try {
      const response = await fetch("/control-api/releases", {
        method: "POST", credentials: "include",
        headers: await readinessMutationHeaders(environmentValidation, csrfToken, { idempotencyKey: crypto.randomUUID() + crypto.randomUUID(), json: true }, true, releaseDraft.environmentId),
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
        const readinessMessage = releaseRequestReadinessMessage(problem?.code);
        if (readinessMessage) throw new Error(readinessMessage);
        throw new Error(problem?.detail ?? "릴리스 요청이 거부되었습니다.");
      }
      const created = await response.json() as { id: string };
      setReleaseId(created.id);
      setRequestNotice(`릴리스 ${created.id} 요청이 생성되었습니다.`);
      await refreshReleases();
      await load(created.id);
    } catch (failure) {
      setError((failure as Error).message);
      restoreFocusAfterRender(trigger);
    } finally {
      requestInFlight.current.release();
      setRequestBusy(false);
    }
  }

  async function validatePrometheusConnection(connectionId: string, trigger: HTMLElement) {
    if (!canManageConnections(sessionUser?.roles ?? []) || connectionBusyId || connectionValidationInFlight.current) return;
    connectionValidationInFlight.current = true;
    setConnectionActionError("");
    setConnectionBusyId(connectionId);
    setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/prometheus/${connectionId}/validate`, {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()),
      });
      const result = await response.json().catch(() => null) as { status?: string; failureCode?: string } | null;
      if (!response.ok) throw new Error("Prometheus 연결 검증 요청이 거부되었습니다.");
      await refreshPrometheusConnections();
      setConnectionNotice(result?.status === "ACTIVE" ? "Prometheus 연결 검증을 통과했습니다." : `Prometheus 연결 검증 실패 · ${result?.failureCode ?? "UNKNOWN"}`);
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    } finally {
      connectionValidationInFlight.current = false;
      setConnectionBusyId("");
    }
  }

  async function validateClusterConnection(connectionId: string, trigger: HTMLElement) {
    if (!canManageConnections(sessionUser?.roles ?? []) || connectionBusyId || connectionValidationInFlight.current) return;
    connectionValidationInFlight.current = true;
    setConnectionActionError("");
    setConnectionBusyId(connectionId);
    setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/clusters/${connectionId}/validate`, {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()),
      });
      const result = await response.json().catch(() => null) as { status?: string; failureCode?: string } | null;
      if (!response.ok) throw new Error("Kubernetes 연결 검증 요청이 거부되었습니다.");
      await refreshClusterConnections();
      setConnectionNotice(result?.status === "ACTIVE" ? "Kubernetes 연결 검증을 통과했습니다." : `Kubernetes 연결 검증 실패 · ${result?.failureCode ?? "UNKNOWN"}`);
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    } finally {
      connectionValidationInFlight.current = false;
      setConnectionBusyId("");
    }
  }

  async function createClusterConnection(event: FormEvent) {
    event.preventDefault();
    const trigger = (event.nativeEvent as SubmitEvent).submitter as HTMLElement | null;
    const validation = clusterConnectionDraftIssue(clusterDraft);
    if (validation) { setConnectionActionError(""); setConnectionValidationIssue({ kind: "cluster", ...validation }); setConnectionNotice(validation.message); restoreFocusAfterRender(document.querySelector<HTMLElement>(`[data-connection-field="cluster-${validation.field}"]`)); return; }
    setConnectionValidationIssue(null);
    setConnectionActionError("");
    setConnectionCreateBusy(true); setConnectionNotice("");
    try {
      const response = await fetch("/control-api/connections/clusters", {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken(), { json: true }),
        body: JSON.stringify({ name: clusterDraft.name.trim(), apiServer: clusterDraft.apiServer.trim(), allowedNamespaces: parseNamespaces(clusterDraft.namespaces), secretRef: clusterDraft.secretRef.trim() }),
      });
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      if (!response.ok) throw new Error(problem?.detail ?? "Kubernetes 연결 등록이 거부되었습니다.");
      setClusterDraft(emptyClusterDraft); await refreshClusterConnections(); setConnectionNotice("Kubernetes 연결을 등록했습니다. 사용 전에 연결 검증을 실행하세요.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionCreateBusy(false); }
  }

  function updateClusterDraft(field: keyof ClusterConnectionDraft, value: string) {
    setClusterDraft((current) => ({ ...current, [field]: value }));
    if (connectionValidationIssue?.kind === "cluster" && connectionValidationIssue.field === field) {
      setConnectionValidationIssue(null);
      setConnectionActionError("");
      setConnectionNotice("");
    }
  }

  function updatePrometheusDraft(field: keyof PrometheusConnectionDraft, value: string) {
    setPrometheusDraft((current) => ({ ...current, [field]: value }));
    if (connectionValidationIssue?.kind === "prometheus" && connectionValidationIssue.field === field) {
      setConnectionValidationIssue(null);
      setConnectionActionError("");
      setConnectionNotice("");
    }
  }

  async function createPrometheusConnection(event: FormEvent) {
    event.preventDefault();
    const trigger = (event.nativeEvent as SubmitEvent).submitter as HTMLElement | null;
    const validation = prometheusConnectionDraftIssue(prometheusDraft);
    if (validation) { setConnectionActionError(""); setConnectionValidationIssue({ kind: "prometheus", ...validation }); setConnectionNotice(validation.message); restoreFocusAfterRender(document.querySelector<HTMLElement>(`[data-connection-field="prometheus-${validation.field}"]`)); return; }
    setConnectionValidationIssue(null);
    setConnectionActionError("");
    setConnectionCreateBusy(true); setConnectionNotice("");
    try {
      const response = await fetch("/control-api/connections/prometheus", {
        method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken(), { json: true }),
        body: JSON.stringify({ name: prometheusDraft.name.trim(), baseUrl: prometheusDraft.baseUrl.trim(), secretRef: prometheusDraft.secretRef.trim() || null, queryTimeoutSeconds: Number(prometheusDraft.queryTimeoutSeconds) }),
      });
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      if (!response.ok) throw new Error(problem?.detail ?? "Prometheus 연결 등록이 거부되었습니다.");
      setPrometheusDraft(emptyPrometheusDraft); await refreshPrometheusConnections(); setConnectionNotice("Prometheus 연결을 등록했습니다. 사용 전에 연결 검증을 실행하세요.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionCreateBusy(false); }
  }

  async function editClusterConnection(item: ClusterConnection, trigger: HTMLElement) {
    if (connectionBusyId) return;
    const name = window.prompt("Kubernetes 연결 이름", item.name); if (name === null) return;
    const apiServer = window.prompt("Kubernetes API HTTPS 주소", item.apiServer); if (apiServer === null) return;
    const namespaces = window.prompt("허용 namespace (쉼표 구분)", item.allowedNamespaces.join(", ")); if (namespaces === null) return;
    const secretRef = window.prompt("Secret reference", item.secretRef); if (secretRef === null) return;
    const draft = { name, apiServer, namespaces, secretRef };
    setConnectionActionError("");
    const validation = validateClusterConnectionDraft(draft); if (validation) { setConnectionNotice(validation); return; }
    setConnectionBusyId(item.id); setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/clusters/${item.id}`, { method: "PUT", credentials: "include", headers: mutationHeaders(await csrfToken(), { json: true }), body: JSON.stringify({ name: name.trim(), apiServer: apiServer.trim(), allowedNamespaces: parseNamespaces(namespaces), secretRef: secretRef.trim() }) });
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      if (!response.ok) throw new Error(problem?.detail ?? "Kubernetes 연결 수정이 거부되었습니다.");
      await refreshClusterConnections(); setConnectionNotice("Kubernetes 연결을 수정했습니다. 변경 사항을 사용하려면 다시 검증하세요.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionBusyId(""); }
  }

  async function editPrometheusConnection(item: PrometheusConnection, trigger: HTMLElement) {
    if (connectionBusyId) return;
    const name = window.prompt("Prometheus 연결 이름", item.name); if (name === null) return;
    const baseUrl = window.prompt("Prometheus HTTP(S) 주소", item.baseUrl); if (baseUrl === null) return;
    const secretRef = window.prompt("Secret reference (인증이 없으면 비움)", item.secretRef ?? ""); if (secretRef === null) return;
    const queryTimeoutSeconds = window.prompt("Query timeout (초)", String(item.queryTimeoutSeconds)); if (queryTimeoutSeconds === null) return;
    const draft = { name, baseUrl, secretRef, queryTimeoutSeconds };
    setConnectionActionError("");
    const validation = validatePrometheusConnectionDraft(draft); if (validation) { setConnectionNotice(validation); return; }
    setConnectionBusyId(item.id); setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/prometheus/${item.id}`, { method: "PUT", credentials: "include", headers: mutationHeaders(await csrfToken(), { json: true }), body: JSON.stringify({ name: name.trim(), baseUrl: baseUrl.trim(), secretRef: secretRef.trim() || null, queryTimeoutSeconds: Number(queryTimeoutSeconds) }) });
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      if (!response.ok) throw new Error(problem?.detail ?? "Prometheus 연결 수정이 거부되었습니다.");
      await refreshPrometheusConnections(); setConnectionNotice("Prometheus 연결을 수정했습니다. 변경 사항을 사용하려면 다시 검증하세요.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionBusyId(""); }
  }

  async function setConnectionEnabled(kind: "clusters" | "prometheus", item: ClusterConnection | PrometheusConnection, trigger: HTMLElement) {
    if (connectionBusyId) return;
    const enable = item.status === "DISABLED";
    if (!enable && !window.confirm(`${item.name} 연결을 비활성화하시겠습니까? 진행 중인 릴리스의 다음 preflight와 새 릴리스 요청이 차단됩니다.`)) return;
    setConnectionActionError("");
    setConnectionBusyId(item.id); setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/${kind}/${item.id}/${enable ? "enable" : "disable"}`, { method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()) });
      if (!response.ok) throw new Error(`연결 ${enable ? "재활성화" : "비활성화"} 요청이 거부되었습니다.`);
      if (kind === "clusters") await refreshClusterConnections(); else await refreshPrometheusConnections();
      setConnectionNotice(enable ? "연결을 재활성화했습니다. 사용 전에 연결 검증을 실행하세요." : "연결을 비활성화했습니다.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionBusyId(""); }
  }

  async function validateAllConnections(kind: "clusters" | "prometheus", trigger: HTMLElement) {
    if (connectionBusyId || connectionValidationInFlight.current) return;
    connectionValidationInFlight.current = true;
    setConnectionActionError("");
    setConnectionBusyId(`all-${kind}`); setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/connections/${kind}/validate`, { method: "POST", credentials: "include", headers: mutationHeaders(await csrfToken()) });
      if (!response.ok) throw new Error(`${kind === "clusters" ? "Kubernetes" : "Prometheus"} 일괄 검증 요청이 거부되었습니다.`);
      const results = await response.json() as { status: string }[];
      if (kind === "clusters") await refreshClusterConnections(); else await refreshPrometheusConnections();
      const failed = results.filter((result) => result.status !== "ACTIVE").length;
      setConnectionNotice(`${kind === "clusters" ? "Kubernetes" : "Prometheus"} ${results.length}개 검증 완료 · 성공 ${results.length - failed} · 실패 ${failed} · 비활성 연결 제외`);
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { connectionValidationInFlight.current = false; setConnectionBusyId(""); }
  }

  async function refreshConnections(trigger: HTMLElement) {
    if (connectionBusyId || connectionRefreshInFlight.current) return;
    connectionRefreshInFlight.current = true;
    setConnectionActionError("");
    setConnectionBusyId("refresh");
    setConnectionNotice("");
    try {
      await Promise.all([refreshClusterConnections(), refreshPrometheusConnections()]);
      setConnectionNotice("외부 연결 목록을 새로고침했습니다.");
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    } finally {
      connectionRefreshInFlight.current = false;
      setConnectionBusyId("");
    }
  }

  async function loadConnectionAudit(aggregateType: "CLUSTER_CONNECTION" | "PROMETHEUS_CONNECTION", connectionId: string, trigger: HTMLElement) {
    if (connectionAuditBusy) return;
    if (connectionAuditId === connectionId) { setConnectionAuditId(""); setConnectionAuditEvents([]); return; }
    setConnectionActionError("");
    setConnectionAuditBusy(true); setConnectionNotice("");
    try {
      const response = await fetch(`/control-api/audit-events?aggregateType=${aggregateType}&aggregateId=${connectionId}`, { credentials: "include" });
      if (!response.ok) throw new Error("연결 감사 이력을 불러올 수 없습니다.");
      const page = await response.json() as { items: AuditEventView[] };
      setConnectionAuditId(connectionId); setConnectionAuditEvents(page.items);
    } catch (failure) {
      const message = (failure as Error).message;
      setConnectionActionError(message);
      setConnectionNotice(message);
      restoreFocusAfterRender(trigger);
    }
    finally { setConnectionAuditBusy(false); }
  }

  async function operate(action: "promote" | "pause" | "resume" | "abort", trigger: HTMLElement | null = null) {
    if (!activeId || operationInFlight.current) return;
    const targetId = activeId;
    const isCurrent = latestReleaseLoad.current.snapshot();
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
      const headers = mutationHeaders(await csrfToken(), { idempotencyKey, json: true });
      if (!isCurrent()) return;
      const response = await fetch(`/control-api/releases/${targetId}/${action}`, {
        method: "POST", credentials: "include",
        headers,
        body: JSON.stringify({ reason }),
      });
      if (!isCurrent()) return;
      if (!response.ok) throw new Error(`${action} 요청이 거부되었습니다.`);
      setOperationNotice(`${action} 요청이 접수되었습니다.`);
      await refreshAudit(targetId, isCurrent);
    } catch (failure) {
      if (isCurrent()) {
        setError((failure as Error).message);
        restoreFocusAfterRender(trigger);
      }
    } finally {
      operationInFlight.current = false;
      setOperationBusy(false);
    }
  }

  async function decide(action: "approve" | "reject", trigger: HTMLElement | null = null) {
    if (!activeId || operationInFlight.current) return;
    if (action === "approve" && !environmentAllowsRelease(approvalEnvironmentValidation)) {
      setError("환경 검증이 만료되었거나 사용할 수 없습니다. 재검증 후 승인하세요.");
      return;
    }
    const targetId = activeId;
    const isCurrent = latestReleaseLoad.current.snapshot();
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
      const headers = await readinessMutationHeaders(approvalEnvironmentValidation, csrfToken, { idempotencyKey, json: true }, action === "approve");
      if (!isCurrent()) return;
      const response = await fetch(`/control-api/releases/${targetId}/${action}`, {
        method: "POST", credentials: "include",
        headers,
        body: JSON.stringify({ reason }),
      });
      if (!isCurrent()) return;
      if (!response.ok) {
        const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null;
        if (problem?.code === "SELF_APPROVAL_NOT_ALLOWED") throw new Error("요청자는 자신의 릴리스를 승인할 수 없습니다.");
        const readinessMessage = approvalReadinessMessage(problem?.code);
        if (action === "approve" && readinessMessage) throw new Error(readinessMessage);
        throw new Error(problem?.detail ?? `${action} 요청이 거부되었습니다.`);
      }
      const decided = await response.json() as { status: string };
      if (!isCurrent()) return;
      setRelease((current) => current?.id === targetId ? { ...current, status: decided.status } : current);
      setLive((current) => ({ ...current, releaseStatus: decided.status }));
      setOperationNotice(`${action === "approve" ? "승인" : "거부"} 결정이 기록되었습니다.`);
      await refreshAudit(targetId, isCurrent);
    } catch (failure) {
      if (isCurrent()) {
        setError((failure as Error).message);
        restoreFocusAfterRender(trigger);
      }
    } finally {
      operationInFlight.current = false;
      setOperationBusy(false);
    }
  }

  const activeReleaseValidationIssue = releaseValidationIssue?.message === error ? releaseValidationIssue : null;
  const releaseFieldAccessibility = (field: keyof ReleaseDraft) => ({
    "data-release-field": field,
    "aria-invalid": activeReleaseValidationIssue?.field === field || undefined,
    "aria-errormessage": activeReleaseValidationIssue?.field === field ? "release-request-error" : undefined,
  });
  const activeConnectionValidationIssue = connectionValidationIssue?.message === connectionNotice ? connectionValidationIssue : null;
  const activeConnectionActionError = Boolean(connectionActionError) && connectionActionError === connectionNotice;
  const connectionFieldAccessibility = (kind: "cluster" | "prometheus", field: keyof ClusterConnectionDraft | keyof PrometheusConnectionDraft) => ({
    "data-connection-field": `${kind}-${field}`,
    "aria-invalid": activeConnectionValidationIssue?.kind === kind && activeConnectionValidationIssue.field === field || undefined,
    "aria-errormessage": activeConnectionValidationIssue?.kind === kind && activeConnectionValidationIssue.field === field ? "connection-form-error" : undefined,
  });

  return (
    <main className={styles.page} aria-labelledby="control-room-title">
      <a className={styles.skipLink} href="#main-content" tabIndex={0}>본문으로 건너뛰기</a>
      <nav className={styles.nav} aria-label="주요 탐색 및 계정 제어">
        <span className={styles.brand}><span className={styles.brandMark}>RP</span>ReleasePilot</span>
        <div className={styles.sessionControls}>{sessionUser && <div className={styles.sessionIdentity} aria-label="현재 로그인 계정"><strong>{sessionUser.email || sessionUser.username || sessionUser.displayName}</strong><small>{sessionUser.demo ? "읽기 전용 데모" : sessionUser.roles.join(" · ")}</small></div>}<span className={styles.live} role="status" aria-live="polite" aria-atomic="true"><i aria-hidden="true" />{sessionConnectionLabel(sessionUser, connection, Boolean(activeId))}</span>{authenticationProviders.oidc && authenticationProviders.loginUrl && <a href={authenticationProviders.loginUrl}>조직 SSO</a>}<button onClick={(event) => void startDemo(event.currentTarget)} disabled={demoBusy || logoutBusy}>읽기 전용 데모</button>{sessionUser && <>{authenticationProviders.oidc && authenticationProviders.loginUrl && <button onClick={(event) => void logout(true, event.currentTarget)} disabled={logoutBusy}>계정 변경</button>}<button onClick={(event) => void logout(false, event.currentTarget)} disabled={logoutBusy}>{logoutBusy ? "로그아웃 중…" : "로그아웃"}</button></>}</div>
      </nav>
      <section className={styles.shell} id="main-content" tabIndex={-1}>
        <header className={styles.topline}>
          <div><p>RELEASE OPERATIONS · CONTROL PLANE</p><h1 id="control-room-title" aria-label="Progressive delivery control room">Progressive delivery<br /><em>without guesswork.</em></h1><span>Canary와 Blue/Green의 판정 근거부터 실행 결과까지 한 화면에서 추적합니다.</span></div>
          <form className={browserStyles.browser} onSubmit={submit}><select aria-label="최근 릴리스" value={releaseId} onChange={(event) => setReleaseId(event.target.value)} disabled={releaseListBusy || releaseLoadBusy}><option value="">{releaseListBusy ? "불러오는 중…" : recentReleases.length ? "릴리스 선택" : "조회 가능한 릴리스 없음"}</option>{recentReleases.map((item) => <option key={item.id} value={item.id}>{releaseOptionLabel(item)}</option>)}</select><button disabled={!releaseId || releaseListBusy || releaseLoadBusy}>{releaseLoadBusy ? "조회 중…" : "불러오기"}</button><button type="button" className={browserStyles.refresh} onClick={(event) => void refreshReleases(event.currentTarget)} disabled={!sessionUser || releaseListBusy || releaseLoadBusy} aria-label="최근 릴리스 새로고침">↻</button></form>
        </header>
        {error && <p id={activeReleaseValidationIssue ? "release-request-error" : undefined} className={styles.error} role="alert" aria-live="assertive" aria-atomic="true">{error}</p>}
        {!release && <p className={styles.sampleNotice} role="note">예시 화면입니다. 아래 릴리스 상태와 판정 근거는 샘플 데이터이며 실제 운영 결과가 아닙니다. 실제 데이터를 확인하려면 최근 릴리스를 선택해 불러오세요.</p>}
        <section className={styles.commandCenter} aria-label="현재 릴리스 운영 요약">
          <div className={styles.commandHeader}>
            <div><span>NOW OBSERVING</span><strong>{title}</strong></div>
            <div className={styles.quickLinks} role="group" aria-label="화면 바로가기"><a href="#release-overview">Overview</a>{canManageConnections(sessionUser?.roles ?? []) && <a href="#release-readiness">Readiness</a>}<a href="#decision-evidence">Evidence</a><a href="#audit-title">Audit</a><a href="#account-security">Account</a></div>
          </div>
          <div className={styles.signalGrid}>
            <div><span>Release state</span><strong data-tone={live.releaseStatus}>{live.releaseStatus}</strong><small>{release ? "실제 릴리스" : "예시 데이터"}</small></div>
            <div><span>Traffic step</span><strong>{activeStep ? `${activeStep.weight}%` : "—"}</strong><small>{activeStep?.status ?? "대기 중"}</small></div>
            <div><span>Latest decision</span><strong data-tone={latest?.verdict ?? "PENDING"}>{latest?.verdict ?? "PENDING"}</strong><small>{latest?.reasonCode ?? "관찰 진행 중"}</small></div>
            <div><span>Evidence coverage</span><strong>{passedEvidence}/{evidence.length}</strong><small>검증 기준 통과</small></div>
          </div>
          <div className={styles.progressTrack} role="progressbar" aria-label="현재 트래픽 단계" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}><i style={{ width: `${progress}%` }} /></div>
        </section>
        {canRequestRelease(sessionUser?.roles ?? []) && <details className={sessionStyles.releaseRequest}>
          <summary>NEW RELEASE REQUEST <span>Developer workflow</span></summary>
          <form onSubmit={(event) => void requestRelease(event)}>
            <label>Project<select required value={projectId} onChange={(event) => selectProject(event.target.value)}><option value="">{projects.length ? "프로젝트 선택" : "활성 프로젝트 없음"}</option>{projects.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.key})</option>)}</select></label>
            <label>Service<select {...releaseFieldAccessibility("serviceId")} required disabled={!projectId} value={releaseDraft.serviceId} onChange={(event) => selectService(event.target.value)}><option value="">{projectId && !services.length ? "활성 서비스 없음" : "서비스 선택"}</option>{services.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.key})</option>)}</select></label>
            <label>Environment<select {...releaseFieldAccessibility("environmentId")} required disabled={!releaseDraft.serviceId} value={releaseDraft.environmentId} onChange={(event) => selectEnvironment(event.target.value)}><option value="">{releaseDraft.serviceId && !environments.length ? "검증된 환경 없음" : "검증된 환경 선택"}</option>{environments.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.strategy}</option>)}</select></label>
            {releaseDraft.environmentId && <section className={sessionStyles.environmentValidation} aria-live="polite"><header><strong>ENVIRONMENT READINESS</strong><div>{environmentValidation && <span data-status={environmentValidation.status}>{environmentValidation.status} · {environmentValidationSummary(environmentValidation, connectionClock)}</span>}{canRevalidateEnvironment(sessionUser?.roles ?? []) && <button type="button" onClick={(event) => void revalidateEnvironment(event.currentTarget)} disabled={environmentValidationBusy || requestBusy}>{environmentValidationBusy ? "재검증 중…" : "지금 재검증"}</button>}</div></header>{environmentValidation ? <><small>최근 점검 {new Date(environmentValidation.checkedAt).toLocaleString("ko-KR")} · 유효 기한 {new Date(environmentValidation.validUntil).toLocaleString("ko-KR")}</small><ul>{environmentValidation.checks.map((check) => <li key={check.code} data-outcome={check.outcome}><b>{check.outcome}</b><span><strong>{check.code}</strong><small>{check.message}</small></span></li>)}</ul></> : <p>{environmentValidationNotice || "최신 점검 결과를 불러오는 중…"}</p>}{environmentValidationNotice && environmentValidation && <p>{environmentValidationNotice}</p>}{canRevalidateEnvironment(sessionUser?.roles ?? []) && <div className={sessionStyles.environmentAudit}><strong>REVALIDATION HISTORY</strong>{environmentAuditEvents.length ? environmentAuditEvents.map((event) => <span key={event.id}>{auditEventLabel(event)} · {new Date(event.occurredAt).toLocaleString("ko-KR")} · chain #{event.chainSequence ?? "—"}</span>) : <span>기록된 재검증 이력이 없습니다.</span>}</div>}</section>}
            <label>Version<input {...releaseFieldAccessibility("version")} required maxLength={100} value={releaseDraft.version} onChange={(event) => updateDraft("version", event.target.value)} placeholder="v1.2.3" /></label>
            <label>Image repository<input {...releaseFieldAccessibility("imageRepository")} required maxLength={500} value={releaseDraft.imageRepository} onChange={(event) => updateDraft("imageRepository", event.target.value)} placeholder="registry.example/team/app" /></label>
            <label className={sessionStyles.wide}>Image digest<input {...releaseFieldAccessibility("imageDigest")} required pattern="sha256:[a-f0-9]{64}" value={releaseDraft.imageDigest} onChange={(event) => updateDraft("imageDigest", event.target.value)} placeholder="sha256:…" /></label>
            <label className={sessionStyles.wide}>Change summary<textarea {...releaseFieldAccessibility("changeSummary")} required maxLength={2000} value={releaseDraft.changeSummary} onChange={(event) => updateDraft("changeSummary", event.target.value)} /></label>
            <label>Commit SHA<input {...releaseFieldAccessibility("commitSha")} required pattern="[a-fA-F0-9]{40}" value={releaseDraft.commitSha} onChange={(event) => updateDraft("commitSha", event.target.value)} /></label>
            <label>Pipeline URL<input {...releaseFieldAccessibility("pipelineUrl")} required type="url" maxLength={1000} value={releaseDraft.pipelineUrl} onChange={(event) => updateDraft("pipelineUrl", event.target.value)} /></label>
            <label className={sessionStyles.wide}>Policy Version ID <small>선택 사항 · 비우면 Environment 기본 정책</small><input {...releaseFieldAccessibility("requestedPolicyVersionId")} value={releaseDraft.requestedPolicyVersionId} onChange={(event) => updateDraft("requestedPolicyVersionId", event.target.value)} placeholder="UUID" /></label>
            <button disabled={requestBusy || environmentValidationBusy || Boolean(releaseDraft.environmentId && !environmentAllowsRelease(environmentValidation, connectionClock, releaseDraft.environmentId))}>{requestBusy ? "요청 중…" : "릴리스 요청"}</button>
          </form>
          {catalogNotice && <p role="alert" aria-live="assertive" aria-atomic="true">{catalogNotice}</p>}
          {requestNotice && <p role="status" aria-live="polite" aria-atomic="true">{requestNotice}</p>}
        </details>}
        {canManageConnections(sessionUser?.roles ?? []) && <section className={sessionStyles.connections} id="release-readiness" aria-labelledby="connections-title">
          <header><div><p>RELEASE READINESS</p><h2 id="connections-title">외부 연결 검증</h2></div><div className={sessionStyles.connectionActions}><button type="button" onClick={(event) => void validateAllConnections("clusters", event.currentTarget)} disabled={Boolean(connectionBusyId)}>{connectionBusyId === "all-clusters" ? "Kubernetes 검증 중…" : "Kubernetes 전체 검증"}</button><button type="button" onClick={(event) => void validateAllConnections("prometheus", event.currentTarget)} disabled={Boolean(connectionBusyId)}>{connectionBusyId === "all-prometheus" ? "Prometheus 검증 중…" : "Prometheus 전체 검증"}</button><button type="button" onClick={(event) => void refreshConnections(event.currentTarget)} disabled={Boolean(connectionBusyId)}>{connectionBusyId === "refresh" ? "새로고침 중…" : "새로고침"}</button></div></header>
          <details className={sessionStyles.connectionCreate}><summary>NEW CONNECTION <span>Operator workflow</span></summary><div className={sessionStyles.connectionForms}>
            <form onSubmit={(event) => void createClusterConnection(event)}><strong>Kubernetes cluster</strong><label>Name<input {...connectionFieldAccessibility("cluster", "name")} required maxLength={100} value={clusterDraft.name} onChange={(event) => updateClusterDraft("name", event.target.value)} /></label><label>API server<input {...connectionFieldAccessibility("cluster", "apiServer")} required type="url" maxLength={500} placeholder="https://…" value={clusterDraft.apiServer} onChange={(event) => updateClusterDraft("apiServer", event.target.value)} /></label><label>Allowed namespaces<input {...connectionFieldAccessibility("cluster", "namespaces")} required placeholder="releasepilot, monitoring" value={clusterDraft.namespaces} onChange={(event) => updateClusterDraft("namespaces", event.target.value)} /></label><label>Secret reference<input {...connectionFieldAccessibility("cluster", "secretRef")} required maxLength={255} placeholder="env:KUBERNETES_TOKEN" value={clusterDraft.secretRef} onChange={(event) => updateClusterDraft("secretRef", event.target.value)} /></label><button disabled={connectionCreateBusy}>{connectionCreateBusy ? "등록 중…" : "Cluster 등록"}</button></form>
            <form onSubmit={(event) => void createPrometheusConnection(event)}><strong>Prometheus</strong><label>Name<input {...connectionFieldAccessibility("prometheus", "name")} required maxLength={100} value={prometheusDraft.name} onChange={(event) => updatePrometheusDraft("name", event.target.value)} /></label><label>Base URL<input {...connectionFieldAccessibility("prometheus", "baseUrl")} required type="url" maxLength={500} placeholder="https://…" value={prometheusDraft.baseUrl} onChange={(event) => updatePrometheusDraft("baseUrl", event.target.value)} /></label><label>Secret reference <small>선택 사항</small><input {...connectionFieldAccessibility("prometheus", "secretRef")} maxLength={255} placeholder="env:PROMETHEUS_TOKEN" value={prometheusDraft.secretRef} onChange={(event) => updatePrometheusDraft("secretRef", event.target.value)} /></label><label>Query timeout seconds<input {...connectionFieldAccessibility("prometheus", "queryTimeoutSeconds")} required type="number" min={1} max={120} value={prometheusDraft.queryTimeoutSeconds} onChange={(event) => updatePrometheusDraft("queryTimeoutSeconds", event.target.value)} /></label><button disabled={connectionCreateBusy}>{connectionCreateBusy ? "등록 중…" : "Prometheus 등록"}</button></form>
          </div></details>
          <h3>Kubernetes clusters</h3>
          <ConnectionFilters query={clusterQuery} filter={clusterFilter} label="Kubernetes 연결" onQuery={setClusterQuery} onFilter={setClusterFilter} />
          {filteredClusterConnections.length ? <div className={sessionStyles.connectionList}>{filteredClusterConnections.map((item) => <article key={item.id}><div><strong>{item.name}</strong><code>{item.apiServer}</code><small data-status={item.status}>{connectionValidationLabel(item)} · namespaces {item.allowedNamespaces.join(", ")}</small></div><div className={sessionStyles.connectionActions}><button type="button" onClick={(event) => void editClusterConnection(item, event.currentTarget)} disabled={Boolean(connectionBusyId)}>편집</button><button type="button" onClick={(event) => void setConnectionEnabled("clusters", item, event.currentTarget)} disabled={Boolean(connectionBusyId)}>{item.status === "DISABLED" ? "재활성화" : "비활성화"}</button><button type="button" onClick={(event) => void loadConnectionAudit("CLUSTER_CONNECTION", item.id, event.currentTarget)} disabled={connectionAuditBusy}>{connectionAuditId === item.id ? "이력 닫기" : "감사 이력"}</button><button type="button" onClick={(event) => void validateClusterConnection(item.id, event.currentTarget)} disabled={Boolean(connectionBusyId) || item.status === "DISABLED"}>{connectionBusyId === item.id ? "처리 중…" : "연결 검증"}</button></div>{connectionAuditId === item.id && <ConnectionAudit events={connectionAuditEvents} />}</article>)}</div> : <p className={sessionStyles.sessionEmpty}>{clusterConnections.length ? "검색 조건에 맞는 Kubernetes 연결이 없습니다." : "등록된 Kubernetes 연결이 없습니다."}</p>}
          <h3>Prometheus</h3>
          <ConnectionFilters query={prometheusQuery} filter={prometheusFilter} label="Prometheus 연결" onQuery={setPrometheusQuery} onFilter={setPrometheusFilter} />
          {filteredPrometheusConnections.length ? <div className={sessionStyles.connectionList}>{filteredPrometheusConnections.map((item) => <article key={item.id}><div><strong>{item.name}</strong><code>{item.baseUrl}</code><small data-status={item.status}>{connectionValidationLabel(item)} · timeout {item.queryTimeoutSeconds}s</small></div><div className={sessionStyles.connectionActions}><button type="button" onClick={(event) => void editPrometheusConnection(item, event.currentTarget)} disabled={Boolean(connectionBusyId)}>편집</button><button type="button" onClick={(event) => void setConnectionEnabled("prometheus", item, event.currentTarget)} disabled={Boolean(connectionBusyId)}>{item.status === "DISABLED" ? "재활성화" : "비활성화"}</button><button type="button" onClick={(event) => void loadConnectionAudit("PROMETHEUS_CONNECTION", item.id, event.currentTarget)} disabled={connectionAuditBusy}>{connectionAuditId === item.id ? "이력 닫기" : "감사 이력"}</button><button type="button" onClick={(event) => void validatePrometheusConnection(item.id, event.currentTarget)} disabled={Boolean(connectionBusyId) || item.status === "DISABLED"}>{connectionBusyId === item.id ? "처리 중…" : "연결 검증"}</button></div>{connectionAuditId === item.id && <ConnectionAudit events={connectionAuditEvents} />}</article>)}</div> : <p className={sessionStyles.sessionEmpty}>{prometheusConnections.length ? "검색 조건에 맞는 Prometheus 연결이 없습니다." : "등록된 Prometheus 연결이 없습니다."}</p>}
          {connectionNotice && <p id={activeConnectionValidationIssue ? "connection-form-error" : undefined} className={sessionStyles.sessionNotice} role={activeConnectionValidationIssue || activeConnectionActionError ? "alert" : "status"} aria-live={activeConnectionValidationIssue || activeConnectionActionError ? "assertive" : "polite"} aria-atomic="true">{connectionNotice}</p>}
        </section>}
        <section className={styles.grid} id="release-overview">
          <article className={styles.releaseCard}>
            <header><div><span>{release ? "SELECTED RELEASE" : "SAMPLE RELEASE · 예시"}</span><h2>{title}</h2></div><b data-status={live.releaseStatus}>{live.releaseStatus}</b></header>
            <div className={styles.meta}><span>현재 단계</span><strong>{activeStep ? `${activeStep.weight}%` : "—"}</strong><span>최근 판정</span><strong>{latest?.verdict ?? "관찰 중"}</strong></div>
            <div className={styles.stages}>{live.steps.map((step) => <div className={styles.stage} data-status={step.status} key={step.index}><i /><span>{step.weight}%</span><small>{step.status}</small></div>)}</div>
            {release && <section className={sessionStyles.approvalContext} aria-label="릴리스 승인 컨텍스트">
              <div><span>대상</span><strong>{release.context.serviceName} · {release.context.environmentName}</strong><small>{release.imageRepository}@{release.imageDigest.slice(0, 19)}…</small></div>
              <div><span>요청자</span><strong>{release.requester.displayName}</strong><small>{release.requester.username} · {new Date(release.createdAt).toLocaleString("ko-KR")}</small></div>
              <div><span>변경</span><strong>{release.changeSummary}</strong><small><a href={release.pipelineUrl} target="_blank" rel="noreferrer">Pipeline ↗</a> · {release.commitSha.slice(0, 12)}</small></div>
              <div><span>정책 snapshot</span><strong>{release.policySnapshot.name} · {release.policySnapshot.definition.strategy}</strong><small>{release.policySnapshot.definition.steps.map((step) => `${step.weight}%/${Math.round(step.minimumObservationSeconds / 60)}m`).join(" → ")}</small></div>
              <ul>{release.policySnapshot.definition.metrics.map((metric, index) => <li key={`${metric.key}:${metric.route ?? "global"}:${index}`}><strong>{metric.key}{metric.route ? ` · ${metric.route}` : ""}</strong><span>{metric.comparison ?? "THRESHOLD"} {metric.threshold}{metric.importance ? ` · ${metric.importance}` : ""}</span></li>)}</ul>
            </section>}
            <footer><button onClick={(event) => void operate("promote", event.currentTarget)} disabled={!activeId || !canOperate || operationBusy}>Promote</button><button onClick={(event) => void operate("pause", event.currentTarget)} disabled={!activeId || !canOperate || operationBusy}>Pause</button><button onClick={(event) => void operate("resume", event.currentTarget)} disabled={!activeId || !canOperate || operationBusy}>Resume</button><button className={styles.danger} onClick={(event) => void operate("abort", event.currentTarget)} disabled={!activeId || !canOperate || operationBusy}>Abort</button>{grafanaUrl && <a href={grafanaUrl} target="_blank" rel="noreferrer">Grafana에서 조사 ↗</a>}</footer>
            {canDecideRelease(sessionUser?.roles ?? [], release?.status) && <><div className={sessionStyles.approvalReadiness} data-ready={environmentAllowsRelease(approvalEnvironmentValidation, connectionClock)} role="status" aria-live="polite" aria-atomic="true"><strong>ENVIRONMENT READINESS</strong><span>{approvalReadinessNotice || approvalReadinessLabel(approvalEnvironmentValidation, connectionClock)}</span></div><div className={sessionStyles.approvalActions}><span>APPROVAL REQUIRED</span><button onClick={(event) => void decide("approve", event.currentTarget)} disabled={operationBusy || !environmentAllowsRelease(approvalEnvironmentValidation, connectionClock)}>Approve</button><button className={styles.danger} onClick={(event) => void decide("reject", event.currentTarget)} disabled={operationBusy}>Reject</button></div></>}
            {operationNotice && <p className={sessionStyles.operationNotice} role="status" aria-live="polite" aria-atomic="true">{operationNotice}</p>}
          </article>
          <aside className={styles.activity}><p>ANALYSIS JOB</p><strong>{latest?.status ?? "EVALUATING"}</strong><dl><div><dt>Attempt</dt><dd>{latest?.attempts ?? 1}</dd></div><div><dt>Verdict</dt><dd>{latest?.verdict ?? "—"}</dd></div><div><dt>Reason</dt><dd>{latest?.reasonCode ?? "관찰 시간 진행 중"}</dd></div></dl><code>{activeId ?? "demo-correlation · 9f31c8"}</code></aside>
        </section>
        <span className={styles.anchor} id="decision-evidence" aria-hidden="true" />
        <section className={styles.evidence}><header><div><p>DECISION EVIDENCE</p><h2>같은 시간창의 stable / canary 비교</h2></div><span>Route 범위와 Query hash로 재현 가능</span></header><div className={styles.table}><div className={styles.rowHead}><span>Metric / Route</span><span>Stable</span><span>Canary</span><span>Threshold</span><span>Result</span></div>{evidence.map((item) => <div className={styles.row} key={`${item.metric_key}:${item.route ?? "global"}`}><span><strong>{item.metric_key}</strong>{item.route && <small className={styles.route}>{item.importance ?? "STANDARD"} · {item.route}</small>}<small>{item.canary_query_hash.slice(0, 12)}…</small></span><span>{format(item.baseline_value, item.metric_key)}</span><span>{format(item.canary_value, item.metric_key)}</span><span>{format(item.threshold, item.metric_key)}</span><b data-verdict={item.verdict}>{item.verdict}</b></div>)}</div></section>
        <section className={auditStyles.timeline} aria-labelledby="audit-title"><header><div><p>AUDIT TIMELINE</p><h2 id="audit-title">릴리스 변경 기록</h2></div><div className={auditStyles.summary}><span>{auditEvents.length} events</span>{canVerifyAudit(sessionUser?.roles ?? []) && <button type="button" onClick={(event) => void refreshAuditIntegrity(event.currentTarget)} disabled={auditIntegrityBusy} data-valid={auditIntegrity?.valid ?? "unknown"}>{auditIntegrityBusy ? "Verifying…" : auditIntegrity ? auditIntegrityLabel(auditIntegrity) : "Verification unavailable"}</button>}</div></header>{canVerifyAudit(sessionUser?.roles ?? []) && auditIntegrity && !auditIntegrity.valid && <p className={auditStyles.integrityAlert} role="alert" aria-live="assertive" aria-atomic="true">감사 체인 검증에 실패했습니다. 자동 승격을 중지하고 이벤트 {auditIntegrity.failedEventId ?? "unknown"}부터 조사하세요.</p>}{auditEvents.length ? <ol>{auditEvents.map((event) => <li key={event.id}><i /><div><strong>{auditEventLabel(event)}</strong><small>correlation {event.correlationId.slice(0, 12)}…{event.chainSequence ? ` · chain #${event.chainSequence}` : ""}</small></div><time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString("ko-KR")}</time></li>)}</ol> : <p className={auditStyles.empty}>{activeId ? "기록된 감사 이벤트가 없습니다." : "릴리스를 선택하면 변경 기록을 확인할 수 있습니다."}</p>}</section>
        <span className={styles.anchor} id="account-security" aria-hidden="true" />
        <section className={sessionStyles.sessions} aria-labelledby="sessions-title">
          <header><div><p>ACCOUNT SECURITY</p><h2 id="sessions-title">활성 세션</h2></div>{canManageSessions(sessionUser) && <button onClick={(event) => void revokeOtherSessions(event.currentTarget)} disabled={sessionBusy || activeSessions.length < 2}>다른 세션 모두 종료</button>}</header>
          {!sessionUser && <p className={sessionStyles.sessionEmpty}>조직 SSO로 로그인하면 활성 세션을 확인하고 원격으로 종료할 수 있습니다.</p>}
          {sessionUser && !sessionUser.demo && authenticationProviders.oidc && <p className={sessionStyles.sessionEmpty}>로그아웃하면 ReleasePilot 세션이 종료됩니다. 다른 계정으로 로그인하려면 상단의 계정 변경을 선택하고 원하는 이메일과 비밀번호로 인증하세요. 조직 SSO도 매번 로그인 화면을 표시합니다.</p>}
          {sessionUser?.demo && <p className={sessionStyles.sessionEmpty}>공유 데모에서는 다른 방문자의 연결을 보호하기 위해 세션 관리가 비활성화됩니다.</p>}
          {canManageSessions(sessionUser) && <div className={sessionStyles.sessionList}>{activeSessions.map((item) => <article key={item.reference}><div><strong>{item.current ? "현재 세션" : "활성 세션"}</strong><code>{item.reference}</code><small>최근 사용 {formatSessionTime(item.lastAccessedAt)} · 만료 {formatSessionTime(item.expiresAt)}</small></div><button onClick={(event) => void revokeSession(item, event.currentTarget)} disabled={sessionBusy}>{item.current ? "로그아웃" : "종료"}</button></article>)}</div>}
          {sessionNotice && <p className={sessionStyles.sessionNotice} role="status" aria-live="polite" aria-atomic="true">{sessionNotice}</p>}
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

function ConnectionFilters({ query, filter, label, onQuery, onFilter }: { query: string; filter: ConnectionFilter; label: string; onQuery: (value: string) => void; onFilter: (value: ConnectionFilter) => void }) {
  return <div className={sessionStyles.connectionFilters}><input type="search" value={query} onChange={(event) => onQuery(event.target.value)} placeholder={`${label} 이름 검색`} aria-label={`${label} 이름 검색`} /><select value={filter} onChange={(event) => onFilter(event.target.value as ConnectionFilter)} aria-label={`${label} 상태 필터`}><option value="ALL">전체 상태</option><option value="NEEDS_ATTENTION">점검 필요</option><option value="ACTIVE">정상</option><option value="DISABLED">비활성</option></select></div>;
}

function ConnectionAudit({ events }: { events: AuditEventView[] }) {
  return <div className={sessionStyles.connectionAudit}>{events.length ? events.map((event) => <span key={event.id}><strong>{auditEventLabel(event)}</strong><small>{connectionAuditDetail(event) ?? "—"} · {new Date(event.occurredAt).toLocaleString("ko-KR")} · chain #{event.chainSequence ?? "—"}</small></span>) : <span>기록된 연결 감사 이벤트가 없습니다.</span>}</div>;
}
