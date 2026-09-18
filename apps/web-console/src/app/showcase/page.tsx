"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import styles from "./showcase.module.css";

type Phase = "REVIEW" | "READY" | "RUNNING" | "ROLLING_BACK" | "ROLLED_BACK" | "COMPLETED";

const trafficSteps = [10, 25, 50, 100];
const errorThreshold = 5;

const phaseCopy: Record<Phase, { label: string; detail: string }> = {
  REVIEW: { label: "승인 검토 중", detail: "운영 승인 전에는 신규 버전에 트래픽을 보내지 않습니다." },
  READY: { label: "배포 승인됨", detail: "승인 기록이 남았습니다. 점진적 릴리스를 시작할 수 있습니다." },
  RUNNING: { label: "점진적 배포 진행 중", detail: "메트릭을 확인하며 신규 버전의 트래픽을 단계적으로 높입니다." },
  ROLLING_BACK: { label: "자동 롤백 실행 중", detail: "오류율이 정책 임계치를 초과해 트래픽을 안정 버전으로 되돌립니다." },
  ROLLED_BACK: { label: "복구 완료", detail: "신규 버전 트래픽을 0%로 복구하고 배포를 중단했습니다." },
  COMPLETED: { label: "배포 완료", detail: "모든 검증 단계를 통과해 신규 버전이 안정 버전이 되었습니다." },
};

export default function Showcase() {
  const [phase, setPhase] = useState<Phase>("REVIEW");
  const [traffic, setTraffic] = useState(0);
  const [errorRate, setErrorRate] = useState(1);

  useEffect(() => {
    if (phase !== "RUNNING") return;
    const timer = window.setTimeout(() => {
      if (errorRate > errorThreshold) {
        setPhase("ROLLING_BACK");
        return;
      }
      const next = trafficSteps.find((step) => step > traffic);
      if (next === undefined) setPhase("COMPLETED");
      else setTraffic(next);
    }, 1200);
    return () => window.clearTimeout(timer);
  }, [errorRate, phase, traffic]);

  useEffect(() => {
    if (phase !== "ROLLING_BACK") return;
    const timer = window.setTimeout(() => {
      setTraffic(0);
      setPhase("ROLLED_BACK");
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [phase]);

  const status = phaseCopy[phase];
  const oldTraffic = 100 - traffic;
  const unsafe = errorRate > errorThreshold;
  const canStart = phase === "READY" || phase === "ROLLED_BACK" || phase === "COMPLETED";
  const eventLog = useMemo(() => {
    if (phase === "REVIEW") return ["배포 요청 생성", "운영 승인 대기"];
    if (phase === "READY") return ["승인 정책 확인", "운영 승인 기록 완료"];
    if (phase === "ROLLING_BACK" || phase === "ROLLED_BACK") return ["메트릭 임계치 초과", "신규 트래픽 차단", "안정 버전 복구"];
    if (phase === "COMPLETED") return ["모든 분석 단계 통과", "신규 버전 승격 완료"];
    return [`신규 버전 트래픽 ${traffic}%`, `오류율 ${errorRate.toFixed(1)}% 관찰 중`];
  }, [errorRate, phase, traffic]);

  function approve() { setPhase("READY"); }
  function start() { setTraffic(0); setPhase("RUNNING"); }
  function reset() { setPhase("REVIEW"); setTraffic(0); setErrorRate(1); }

  return (
    <main className={styles.page}>
      <nav className={styles.nav} aria-label="쇼케이스 탐색">
        <Link href="/" className={styles.brand}><span>RP</span>ReleasePilot</Link>
        <Link href="/" className={styles.consoleLink}>Control room 열기 <span aria-hidden="true">↗</span></Link>
      </nav>

      <section className={styles.hero} aria-labelledby="showcase-title">
        <p className={styles.eyebrow}>PROGRESSIVE DELIVERY CONTROL PLANE</p>
        <h1 id="showcase-title">배포를 실행하는 도구가 아니라,<br /><em>안전한 결정을 운영하는 플랫폼.</em></h1>
        <p className={styles.lead}>ReleasePilot은 릴리스 일정을 관리하는 프로젝트 도구가 아닙니다. 배포 승인, 트래픽 전환, 메트릭 판정과 자동 롤백을 하나의 감사 가능한 흐름으로 연결합니다.</p>
        <div className={styles.capabilities} aria-label="ReleasePilot 핵심 기능">
          <article><span>01</span><strong>승인 검토</strong><p>정책과 readiness를 확인하고 책임 있는 승인 기록을 남깁니다.</p></article>
          <article><span>02</span><strong>트래픽 제어</strong><p>검증된 단계만큼 Canary 트래픽을 점진적으로 확대합니다.</p></article>
          <article><span>03</span><strong>자동 롤백</strong><p>오류율이 임계치를 넘으면 즉시 안정 버전으로 복구합니다.</p></article>
        </div>
      </section>

      <section className={styles.simulator} aria-labelledby="simulator-title">
        <header className={styles.simHeader}>
          <div><p className={styles.eyebrow}>INTERACTIVE SHOWCASE</p><h2 id="simulator-title">Canary deployment simulator</h2></div>
          <div className={styles.liveBadge}><i /> LIVE POLICY · ERROR ≤ {errorThreshold}%</div>
        </header>

        <div className={styles.workspace}>
          <section className={styles.stage} aria-label="트래픽 라우팅 시각화">
            {unsafe && (phase === "RUNNING" || phase === "ROLLING_BACK") && <div className={styles.alert} role="alert">오류율 {errorRate.toFixed(1)}% · 정책 임계치 {errorThreshold}% 초과</div>}
            <div className={styles.router}><span>LOAD</span><strong>BALANCER</strong></div>
            <div className={`${styles.route} ${styles.oldRoute}`}><i style={{ width: `${oldTraffic}%` }} /></div>
            <div className={`${styles.route} ${styles.newRoute}`}><i style={{ width: `${traffic}%` }} /></div>
            <div className={`${styles.version} ${styles.oldVersion}`}><span>STABLE</span><strong>v1.8.4</strong><b>{oldTraffic}% traffic</b></div>
            <div className={`${styles.version} ${styles.newVersion}`} data-active={traffic > 0}><span>CANARY</span><strong>v1.9.0</strong><b>{traffic}% traffic</b></div>
            <div className={styles.packets} aria-hidden="true">
              {Array.from({ length: 18 }, (_, index) => <i key={index} style={{ "--index": index } as React.CSSProperties} data-canary={index < Math.round(traffic / 6)} />)}
            </div>
          </section>

          <aside className={styles.panel} aria-live="polite">
            <div className={styles.phase} data-phase={phase}><span>현재 상태</span><strong>{status.label}</strong><p>{status.detail}</p></div>
            <dl className={styles.metrics}>
              <div><dt>Canary traffic</dt><dd>{traffic}%</dd></div>
              <div><dt>Observed error</dt><dd data-unsafe={unsafe}>{errorRate.toFixed(1)}%</dd></div>
              <div><dt>Policy threshold</dt><dd>{errorThreshold.toFixed(1)}%</dd></div>
            </dl>
            <div className={styles.timeline} aria-label="현재 처리 기록">{eventLog.map((event, index) => <p key={event}><i data-done={index < eventLog.length - 1} />{event}</p>)}</div>
          </aside>
        </div>

        <div className={styles.controls}>
          <div className={styles.actions}>
            {phase === "REVIEW" ? <button className={styles.primary} onClick={approve}>배포 승인</button> : <button className={styles.primary} onClick={start} disabled={!canStart}>{phase === "RUNNING" || phase === "ROLLING_BACK" ? "배포 진행 중…" : "점진적 배포 시작"}</button>}
            <button className={styles.secondary} onClick={reset}>시뮬레이터 초기화</button>
          </div>
          <label className={styles.errorControl}>
            <span><strong>테스트 오류율</strong><small>배포 중 5%를 넘겨 자동 롤백을 확인해 보세요.</small></span>
            <input aria-label="테스트 오류율" type="range" min="0" max="20" step="1" value={errorRate} onChange={(event) => setErrorRate(Number(event.target.value))} />
            <output>{errorRate}%</output>
          </label>
        </div>
      </section>

      <section className={styles.explainer} aria-labelledby="difference-title">
        <p className={styles.eyebrow}>WHY RELEASEPILOT</p>
        <h2 id="difference-title">일정 관리가 끝나는 곳에서<br />ReleasePilot의 일이 시작됩니다.</h2>
        <p>Jira나 GitHub가 “언제 무엇을 배포할지” 조율한다면, ReleasePilot은 “지금 이 버전을 운영 트래픽에 더 노출해도 안전한지”를 증거로 판단하고 실행합니다.</p>
      </section>
    </main>
  );
}
