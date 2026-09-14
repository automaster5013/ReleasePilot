"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import styles from "./page.module.css";

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
];
const grafanaUrl = process.env.NEXT_PUBLIC_GRAFANA_URL;

export default function Home() {
  const [releaseId, setReleaseId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [release, setRelease] = useState<Release | null>(null);
  const [live, setLive] = useState<LiveState>({ releaseStatus: "ANALYZING", steps: demoSteps });
  const [analyses, setAnalyses] = useState<Analysis[]>([{ id: "demo", stepIndex: 2, status: "COMPLETED", attempts: 1, verdict: "PASS", reasonCode: "ALL_RULES_PASSED", evidence: demoEvidence }]);
  const [connection, setConnection] = useState("DEMO SNAPSHOT");
  const [error, setError] = useState("");
  const [canOperate, setCanOperate] = useState(false);

  async function startDemo() {
    const csrf = await fetch("/control-api/session/csrf", { credentials: "include" }).then((response) => response.json());
    const response = await fetch("/control-api/session/demo", { method: "POST", credentials: "include", headers: { [csrf.headerName]: csrf.token } });
    if (!response.ok) { setError("공개 데모 세션을 시작할 수 없습니다."); return; }
    const session = await response.json();
    setCanOperate(session.user.roles.includes("OPERATOR"));
    setConnection("DEMO · VIEW ONLY");
  }

  async function load(id: string) {
    setError("");
    const [releaseResponse, analysesResponse] = await Promise.all([
      fetch(`/control-api/releases/${id}`, { credentials: "include" }),
      fetch(`/control-api/releases/${id}/analyses`, { credentials: "include" }),
    ]);
    if (!releaseResponse.ok || !analysesResponse.ok) throw new Error("릴리스 조회 권한 또는 ID를 확인하세요.");
    setRelease(await releaseResponse.json());
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

  async function operate(action: "promote" | "pause" | "abort") {
    if (!activeId) return;
    const response = await fetch(`/control-api/releases/${activeId}/${action}`, {
      method: "POST", credentials: "include",
      headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() + crypto.randomUUID() },
      body: JSON.stringify({ reason: `Operator ${action} from release console` }),
    });
    if (!response.ok) setError(`${action} 요청이 거부되었습니다.`);
  }

  return (
    <main className={styles.page}>
      <nav className={styles.nav}>
        <span className={styles.brand}><span className={styles.brandMark}>RP</span>ReleasePilot</span>
        <span className={styles.live}><i />{connection}<button onClick={startDemo}>읽기 전용 데모</button></span>
      </nav>
      <section className={styles.shell}>
        <header className={styles.topline}>
          <div><p>RELEASE OPERATIONS</p><h1>Canary control room</h1><span>판정 근거부터 실행 결과까지 한 화면에서 추적합니다.</span></div>
          <form onSubmit={submit}><input aria-label="Release ID" placeholder="Release UUID" value={releaseId} onChange={(event) => setReleaseId(event.target.value)} /><button>불러오기</button></form>
        </header>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <section className={styles.grid}>
          <article className={styles.releaseCard}>
            <header><div><span>PRODUCTION RELEASE</span><h2>{title}</h2></div><b data-status={live.releaseStatus}>{live.releaseStatus}</b></header>
            <div className={styles.meta}><span>현재 단계</span><strong>{activeStep ? `${activeStep.weight}%` : "—"}</strong><span>최근 판정</span><strong>{latest?.verdict ?? "관찰 중"}</strong></div>
            <div className={styles.stages}>{live.steps.map((step) => <div className={styles.stage} data-status={step.status} key={step.index}><i /><span>{step.weight}%</span><small>{step.status}</small></div>)}</div>
            <footer><button onClick={() => operate("promote")} disabled={!activeId || !canOperate}>Promote</button><button onClick={() => operate("pause")} disabled={!activeId || !canOperate}>Pause</button><button className={styles.danger} onClick={() => operate("abort")} disabled={!activeId || !canOperate}>Abort</button>{grafanaUrl && <a href={grafanaUrl} target="_blank" rel="noreferrer">Grafana에서 조사 ↗</a>}</footer>
          </article>
          <aside className={styles.activity}><p>ANALYSIS JOB</p><strong>{latest?.status ?? "EVALUATING"}</strong><dl><div><dt>Attempt</dt><dd>{latest?.attempts ?? 1}</dd></div><div><dt>Verdict</dt><dd>{latest?.verdict ?? "—"}</dd></div><div><dt>Reason</dt><dd>{latest?.reasonCode ?? "관찰 시간 진행 중"}</dd></div></dl><code>{activeId ?? "demo-correlation · 9f31c8"}</code></aside>
        </section>
        <section className={styles.evidence}><header><div><p>DECISION EVIDENCE</p><h2>같은 시간창의 stable / canary 비교</h2></div><span>Query hash로 재현 가능</span></header><div className={styles.table}><div className={styles.rowHead}><span>Metric</span><span>Stable</span><span>Canary</span><span>Threshold</span><span>Result</span></div>{evidence.map((item) => <div className={styles.row} key={item.metric_key}><span><strong>{item.metric_key}</strong><small>{item.canary_query_hash.slice(0, 12)}…</small></span><span>{format(item.baseline_value, item.metric_key)}</span><span>{format(item.canary_value, item.metric_key)}</span><span>{format(item.threshold, item.metric_key)}</span><b data-verdict={item.verdict}>{item.verdict}</b></div>)}</div></section>
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
