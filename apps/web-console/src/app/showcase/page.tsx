"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import styles from "./showcase.module.css";

type Phase = "REVIEW" | "READY" | "RUNNING" | "ROLLING_BACK" | "ROLLED_BACK" | "COMPLETED";
type EvidenceState = "pending" | "active" | "passed" | "blocked";
type EvidenceRecord = { id: string; label: string; evidence: string; state: EvidenceState; actor: string; source: string; recordedAt: string };
type IntegrityState = "ready" | "verifying" | "verified" | "failed";

const trafficSteps = [10, 25, 50, 100];
const errorThreshold = 5;
const stablePath = "M 176 239 C 286 197, 393 125, 520 120";
const canaryPath = "M 176 261 C 286 303, 393 375, 520 380";

async function hashEvidenceChain(records: EvidenceRecord[]) {
  let previous = "GENESIS";
  const hashes: string[] = [];
  for (const record of records) {
    const canonical = JSON.stringify([record.id, record.label, record.evidence, record.state, record.actor, record.source, record.recordedAt]);
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${previous}:${canonical}`));
    previous = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
    hashes.push(previous);
  }
  return hashes;
}

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
  const [selectedEvidence, setSelectedEvidence] = useState(0);
  const [sealedHashes, setSealedHashes] = useState<string[]>([]);
  const [integrity, setIntegrity] = useState<IntegrityState>("ready");

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
  const rollingBack = phase === "ROLLING_BACK";
  const recovered = phase === "ROLLED_BACK";
  const approved = phase !== "REVIEW";
  const canStart = phase === "READY" || phase === "ROLLED_BACK" || phase === "COMPLETED";
  const evidenceChain = useMemo(() => {
    const failed = phase === "ROLLING_BACK" || phase === "ROLLED_BACK";
    const steps: EvidenceRecord[] = [
      { id: "APR-042", label: "운영 승인", evidence: approved ? "readiness + policy 봉인" : "책임자 서명 대기", state: approved ? "passed" : "active", actor: "on-call approver", source: "Policy v3 + readiness", recordedAt: approved ? "T+00:04" : "대기 중" },
      ...trafficSteps.map((step) => {
        const passed = traffic > step || phase === "COMPLETED";
        const active = phase === "RUNNING" && traffic === step;
        const blocked = failed && traffic === step;
        return {
          id: `OBS-${String(step).padStart(3, "0")}`,
          label: `Canary ${step}%`,
          evidence: blocked ? `${errorRate.toFixed(1)}% > ${errorThreshold.toFixed(1)}% · 차단` : passed ? `${errorRate.toFixed(1)}% ≤ ${errorThreshold.toFixed(1)}% · 통과` : active ? `오류율 ${errorRate.toFixed(1)}% 관측 중` : "메트릭 판정 대기",
          state: blocked ? "blocked" : passed ? "passed" : active ? "active" : "pending",
          actor: "analysis-worker",
          source: `Prometheus · ${step}% window`,
          recordedAt: passed || active || blocked ? `T+${String(8 + trafficSteps.indexOf(step) * 2).padStart(2, "0")}:00` : "대기 중",
        } as const;
      }),
      { id: failed ? "RBK-017" : "PRM-019", label: failed ? "자동 롤백" : "Stable 승격", evidence: phase === "ROLLED_BACK" ? "Stable 100% · Canary 격리" : phase === "ROLLING_BACK" ? "트래픽 복구 실행 중" : phase === "COMPLETED" ? "v1.9.0 승격 증거 봉인" : "최종 판정 대기", state: phase === "ROLLED_BACK" || phase === "COMPLETED" ? "passed" : phase === "ROLLING_BACK" ? "active" : "pending", actor: "release-controller", source: failed ? "Policy breach action" : "All gates passed", recordedAt: phase === "ROLLED_BACK" || phase === "COMPLETED" ? "T+16:00" : "대기 중" },
    ];
    return steps;
  }, [approved, errorRate, phase, traffic]);

  useEffect(() => {
    let current = true;
    hashEvidenceChain(evidenceChain).then((hashes) => {
      if (!current) return;
      setSealedHashes(hashes);
      setIntegrity("ready");
    });
    return () => { current = false; };
  }, [evidenceChain]);

  async function verifyIntegrity() {
    setIntegrity("verifying");
    const recalculated = await hashEvidenceChain(evidenceChain);
    setIntegrity(recalculated.every((hash, index) => hash === sealedHashes[index]) ? "verified" : "failed");
  }

  function approve() { setPhase("READY"); }
  function start() { setTraffic(0); setPhase("RUNNING"); }
  function reset() { setPhase("REVIEW"); setTraffic(0); setErrorRate(1); setSelectedEvidence(0); }

  return (
    <main className={styles.page}>
      <nav className={styles.nav} aria-label="쇼케이스 탐색">
        <Link href="/showcase" className={styles.brand}><span>RP</span>ReleasePilot</Link>
        <Link href="/console" className={styles.consoleLink}>Control room 열기 <span aria-hidden="true">↗</span></Link>
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
            <svg className={styles.deliveryMap} viewBox="0 0 720 500" role="img" aria-labelledby="delivery-map-title delivery-map-description">
              <title id="delivery-map-title">ReleasePilot 실시간 Canary 트래픽 제어</title>
              <desc id="delivery-map-description">Policy Core가 안정 버전과 Canary 버전의 트래픽을 제어하며, 10, 25, 50, 100퍼센트 검증 단계를 통과하거나 오류율 초과 시 자동 롤백합니다.</desc>
              <defs>
                <linearGradient id="stable-flow" x1="0" y1="0" x2="1" y2="0"><stop stopColor="#78a8ff" stopOpacity=".28"/><stop offset="1" stopColor="#8eb7ff"/></linearGradient>
                <linearGradient id="canary-flow" x1="0" y1="0" x2="1" y2="0"><stop stopColor="#42d99b" stopOpacity=".35"/><stop offset="1" stopColor="#69f0b7"/></linearGradient>
                <radialGradient id="core-fill"><stop stopColor="#17372e"/><stop offset="1" stopColor="#0b1714"/></radialGradient>
                <filter id="flow-glow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="4" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
                <filter id="core-glow" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="12"/></filter>
              </defs>

              <g className={styles.mapGrid} aria-hidden="true">
                {Array.from({ length: 12 }, (_, index) => <line key={`v-${index}`} x1={index * 60} y1="0" x2={index * 60} y2="500" />)}
                {Array.from({ length: 9 }, (_, index) => <line key={`h-${index}`} x1="0" y1={index * 60 + 10} x2="720" y2={index * 60 + 10} />)}
              </g>

              <path className={styles.routeBed} d={stablePath}/>
              <path className={styles.routeBed} d={canaryPath}/>
              <path className={`${styles.flowLine} ${styles.stableFlow}`} data-route="stable" d={stablePath} pathLength="100" style={{ "--flow-level": oldTraffic } as React.CSSProperties}/>
              <path className={`${styles.flowLine} ${styles.canaryFlow}`} data-route="canary" data-rollback={rollingBack} d={canaryPath} pathLength="100" style={{ "--flow-level": traffic } as React.CSSProperties}/>

              <g className={styles.gates} aria-hidden="true">
                {[
                  [268, 298, 10], [337, 334, 25], [406, 365, 50], [492, 379, 100],
                ].map(([x, y, step]) => <g key={step} transform={`translate(${x} ${y})`} data-passed={traffic >= step}><circle r="8"/><circle r="3"/><text y="-15">{step}%</text></g>)}
              </g>

              {(phase === "RUNNING" || phase === "COMPLETED") && Array.from({ length: 5 }, (_, index) => (
                <circle className={styles.canaryPacket} r="4" key={`canary-${index}`} aria-hidden="true">
                  <animateMotion dur="2.8s" begin={`${index * -.56}s`} repeatCount="indefinite" path={canaryPath}/>
                </circle>
              ))}
              {rollingBack && Array.from({ length: 6 }, (_, index) => (
                <circle className={styles.rollbackPacket} r="4" key={`rollback-${index}`} aria-hidden="true">
                  <animateMotion dur="1.2s" begin={`${index * -.2}s`} repeatCount="indefinite" path={canaryPath} keyPoints="1;0" keyTimes="0;1" calcMode="linear"/>
                </circle>
              ))}
              {Array.from({ length: 4 }, (_, index) => (
                <circle className={styles.stablePacket} r="3.5" key={`stable-${index}`} aria-hidden="true">
                  <animateMotion dur="3.2s" begin={`${index * -.8}s`} repeatCount="indefinite" path={stablePath}/>
                </circle>
              ))}

              <g className={styles.approvalLink} data-approved={approved} aria-hidden="true"><path d="M120 142V180"/><circle cx="120" cy="180" r="3"/></g>
              <g className={styles.approvalGate} data-approved={approved} transform="translate(34 78)">
                <rect width="172" height="64" rx="14"/>
                <g className={styles.approvalIcon} transform="translate(18 17)"><rect x="0" y="9" width="22" height="18" rx="5"/><path d={approved ? "M6 17l4 4 7-8" : "M6 9V6a5 5 0 0110 0v3"}/></g>
                <text className={styles.approvalKicker} x="52" y="20">APPROVAL GATE</text>
                <text className={styles.approvalState} x="52" y="39">{approved ? "APPROVED · EVIDENCE SEALED" : "APPROVAL REQUIRED"}</text>
                <text className={styles.approvalEvidence} x="52" y="54">READINESS {approved ? "✓" : "○"}  ·  POLICY {approved ? "✓" : "○"}</text>
              </g>

              <g className={styles.core} transform="translate(120 250)">
                <circle className={styles.coreAura} r="72"/>
                <circle className={styles.coreOrbit} r="58"/>
                <path className={styles.coreMark} d="M0-34 30-17 30 17 0 34-30 17-30-17Z"/>
                <text className={styles.coreKicker} y="-7">RELEASEPILOT</text>
                <text className={styles.coreName} y="12">POLICY CORE</text>
                <text className={styles.coreStatus} y="30">{rollingBack ? "CUTOVER" : recovered ? "RECOVERED" : phase === "COMPLETED" ? "PROMOTED" : phase === "REVIEW" ? "LOCKED" : "ROUTING LIVE"}</text>
              </g>

              <g className={`${styles.releaseNode} ${styles.stableNode}`} data-release="stable" transform="translate(520 65)">
                <rect width="168" height="110" rx="17"/>
                <circle cx="22" cy="24" r="4"/><text className={styles.nodeKicker} x="34" y="28">STABLE</text>
                <text className={styles.nodeVersion} x="20" y="61">v1.8.4</text>
                <text className={styles.nodeTraffic} x="20" y="88">{oldTraffic}% traffic</text>
                <text className={styles.nodeHealth} x="148" y="28" textAnchor="end">HEALTHY</text>
              </g>
              <g className={`${styles.releaseNode} ${styles.canaryNode}`} data-release="canary" transform="translate(520 325)" data-active={traffic > 0} data-state={rollingBack ? "rollback" : recovered ? "quarantined" : phase === "COMPLETED" ? "promoted" : "normal"}>
                <rect width="168" height="110" rx="17"/>
                <circle cx="22" cy="24" r="4"/><text className={styles.nodeKicker} x="34" y="28">CANARY</text>
                <text className={styles.nodeVersion} x="20" y="61">v1.9.0</text>
                <text className={styles.nodeTraffic} x="20" y="88">{traffic}% traffic</text>
                <text className={styles.nodeHealth} x="148" y="28" textAnchor="end">{rollingBack ? "BLOCKED" : recovered ? "QUARANTINED" : phase === "COMPLETED" ? "PROMOTED" : traffic > 0 ? "ANALYZING" : "STANDBY"}</text>
              </g>
              {(rollingBack || recovered) && <g className={styles.rollbackSeal} transform="translate(500 380)" aria-hidden="true"><circle r="15"/><path d="M-5-5 5 5M5-5-5 5"/></g>}
              {recovered && <g className={styles.recoveryStamp} transform="translate(255 424)"><rect width="245" height="34" rx="17"/><circle cx="18" cy="17" r="4"/><text x="32" y="21">TRAFFIC RESTORED · CANARY ISOLATED</text></g>}
            </svg>
          </section>

          <aside className={styles.panel} aria-live="polite">
            <div className={styles.phase} data-phase={phase}><span>현재 상태</span><strong>{status.label}</strong><p>{status.detail}</p></div>
            <dl className={styles.metrics}>
              <div><dt>Canary traffic</dt><dd>{traffic}%</dd></div>
              <div><dt>Observed error</dt><dd data-unsafe={unsafe}>{errorRate.toFixed(1)}%</dd></div>
              <div><dt>Policy threshold</dt><dd>{errorThreshold.toFixed(1)}%</dd></div>
            </dl>
            <section className={styles.evidence} aria-labelledby="evidence-title">
              <div className={styles.evidenceHeader}><h3 id="evidence-title">감사 증거 체인</h3><button type="button" onClick={verifyIntegrity} disabled={integrity === "verifying" || sealedHashes.length !== evidenceChain.length}>{integrity === "verified" ? "6/6 HASH VERIFIED" : integrity === "failed" ? "INTEGRITY FAILED" : integrity === "verifying" ? "VERIFYING…" : "체인 무결성 검증"}</button></div>
              <ol>{evidenceChain.map((item, index) => <li key={item.id} data-state={item.state}><i aria-hidden="true" /><button type="button" onClick={() => setSelectedEvidence(index)} aria-current={selectedEvidence === index ? "step" : undefined}><span>{item.id}</span><strong>{item.label}</strong><small>{item.evidence}</small></button><b>{item.state === "passed" ? "검증" : item.state === "blocked" ? "차단" : item.state === "active" ? "기록 중" : "대기"}</b></li>)}</ol>
              <div className={styles.receipt} aria-live="polite"><div><span>검증 영수증 · {evidenceChain[selectedEvidence].id}</span><b>{evidenceChain[selectedEvidence].recordedAt}</b></div><p><strong>{evidenceChain[selectedEvidence].actor}</strong> · {evidenceChain[selectedEvidence].source}</p><small>PREV {selectedEvidence === 0 ? "GENESIS" : `${sealedHashes[selectedEvidence - 1]?.slice(0, 8) ?? "계산 중"}…`} → SHA {sealedHashes[selectedEvidence]?.slice(0, 8) ?? "계산 중"}…</small></div>
              <p className={styles.chainSeal}>{phase === "COMPLETED" ? "✓ CHAIN SEALED · PROMOTED" : recovered ? "✓ CHAIN SEALED · RECOVERED" : "SHA-256 · APPEND ONLY"}</p>
            </section>
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

        <div className={styles.comparison} role="table" aria-label="릴리스 일정 관리 도구와 ReleasePilot 비교">
          <div className={styles.comparisonHead} role="row"><span role="columnheader">비교 기준</span><strong role="columnheader">일정·프로젝트 관리</strong><strong role="columnheader">ReleasePilot</strong></div>
          {[
            ["핵심 질문", "언제, 누가, 무엇을 배포하는가?", "지금 더 많은 트래픽을 보내도 안전한가?"],
            ["판단 입력", "이슈, 일정, 담당자, 진행 상태", "승인 증거, readiness, 실시간 운영 메트릭"],
            ["실행 동작", "업무 조율과 상태 추적", "Canary 트래픽 확대·중지·승격"],
            ["이상 감지", "담당자에게 상황 공유", "정책 임계치로 자동 판정"],
            ["장애 대응", "복구 작업을 별도로 조율", "트래픽 차단과 안정 버전 자동 롤백"],
            ["남는 증거", "업무 변경 이력", "승인·메트릭·트래픽·복구 감사 체인"],
          ].map(([label, coordination, releasePilot]) => <div className={styles.comparisonRow} role="row" key={label}><span role="rowheader">{label}</span><span role="cell">{coordination}</span><strong role="cell">{releasePilot}</strong></div>)}
        </div>

        <div className={styles.faq} aria-labelledby="faq-title">
          <div><p className={styles.eyebrow}>QUICK ANSWERS</p><h3 id="faq-title">자주 묻는 질문</h3></div>
          <div>
            <details open><summary>ReleasePilot은 릴리스 일정 관리 도구인가요?</summary><p>아닙니다. ReleasePilot은 승인 증거와 운영 메트릭을 바탕으로 Canary 트래픽을 제어하고 이상 시 자동 롤백하는 Progressive Delivery Control Plane입니다.</p></details>
            <details><summary>기존 CI/CD와 함께 사용할 수 있나요?</summary><p>네. 기존 파이프라인이 만든 배포 후보를 받아 승인, 점진적 트래픽 전환, 메트릭 판정, 롤백과 감사 기록을 운영합니다.</p></details>
            <details><summary>오류율이 임계치를 넘으면 어떻게 되나요?</summary><p>신규 버전으로 향하는 트래픽을 차단하고 안정 버전으로 복구하며, 판단 근거와 조치 이력을 남깁니다.</p></details>
          </div>
        </div>
      </section>
    </main>
  );
}
