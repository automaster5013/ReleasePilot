# ReleasePilot 작업 일정표

이 문서는 ReleasePilot의 초기 MVP 구축부터 운영 안정화, 공개 쇼케이스, GitHub Checks 실연동까지 실제 진행한 작업을 일정 순서로 요약한다. 세부 192개 항목과 각 테스트·배포 증거는 [MVP 실행 백로그](mvp-backlog.md)에 있다.

## 완료 요약

- 작업 기간: **2026-09-14 ~ 2026-09-19**
- 완료 범위: **M0~M7 및 후속 우선순위 192/192**
- 최종 문서 기준 commit: `183fb74`
- GitHub Checks 기능 commit: `a8a7ab4`, 오탐 수정 `18b90ee`
- 최종 GitOps commit: `6d7719a`
- 최종 Control Plane digest: `sha256:dad3db6359319a0688e7176dc52a122709d176ca305a798c457ee40e2aac26f9`
- 최종 검증: CI #369, CD #124, 문서 CI #370·#371 성공

## 일자별 진행표

| 날짜 | 계획/초점 | 주요 완료 내용 | 대표 검증 |
| --- | --- | --- | --- |
| 2026-09-14 | MVP 기반과 첫 공개 배포 | 모노레포, Control Plane·Worker·Web Console, MySQL, 인증·카탈로그, 릴리스 승인, Argo Rollouts, Prometheus 판정, 관측성 UI, AWS 공개 데모를 구축했다. Blue/Green, OIDC/SSO, 다중 클러스터, route 중요도 정책, 감사 SHA-256 체인과 S3 Object Lock 보관도 연결했다. | 최초 commit `d4f6c91`, M0~M7 완료, EKS/Argo CD/HTTPS 공개 운영 |
| 2026-09-15 | 보안·운영 내구성 강화 | 인증 rate limit, 활성 세션 관리, Environment·Kubernetes·Prometheus 신선도 검사, 요청·승인·실행 직전 fail-closed preflight, secret redaction, timeout·재시도, 실제 MySQL 통합, 감사 체인·아카이브 복구를 확장했다. | v0.49.0~v0.55.0, 서버·MySQL 통합 회귀, 반복 Canary 배포 |
| 2026-09-16 | 동시성·접근성·운영 경계 | 릴리스·연결·세션·감사 작업의 동시성 잠금과 지연 응답 격리, CSRF·멱등성, 오류 focus 복원, 상태 메시지, 키보드·고대비·모바일 reflow를 보강했다. | Chromium/Firefox/WebKit, WCAG A·AA 자동 검사, 공개 ingress·보안 헤더 검증 |
| 2026-09-17 | 실패 복구와 사용자 흐름 완성 | 조회·mutation 실패, timeout, 다중 탭 중복 실행, 새로고침·대상 전환 중 race condition을 체계적으로 재현하고 복구 동작을 추가했다. 운영 콘솔의 요청→승인→Rollout→감사 흐름을 역할별로 완성했다. | CI/CD 연속 성공, 브라우저 회귀 확대, Pod 재시작 0·Rollout 2/2 Healthy |
| 2026-09-18 | 공개 쇼케이스와 감사 증거 시각화 | 공개 `/showcase`, Canary 트래픽 시뮬레이터, 승인 게이트, 자동 롤백, Policy Core, 검색 metadata·JSON-LD·소셜 이미지, 한 화면 반응형 UI를 구현했다. 감사 증거 영수증과 실제 SHA-256 검증·변조 탐지도 추가했다. | CI #353~#363 계열, GitOps digest 배포, 1440×900·320/390px·reduced-motion 검증 |
| 2026-09-19 | 무결성 보고서와 GitHub Checks 완결 | 변조 영향 범위 진단과 JSON 보고서 내보내기를 완료했다. 저장소 전용 GitHub App을 설치하고 RS256 App JWT→installation token 자동 발급·캐시를 구현해 EKS Secret으로 배포했다. | 전체 Control Plane 205개 테스트, CI #369, CD #124, Check Run `105735510576`, CI #370·#371 |

## 마일스톤 일정

| 마일스톤 | 원래 예상 | 실제 완료 | 결과 |
| --- | ---: | --- | --- |
| M0 — 저장소와 개발 기준선 | 1주 | 2026-09-14 | 모노레포, 세 애플리케이션, Compose, CI, secret scan |
| M1 — 인증과 카탈로그 | 1.5주 | 2026-09-14 | 세션·CSRF·RBAC, Project/Service, 외부 연결과 Environment |
| M2 — 릴리스 요청과 승인 | 1.5주 | 2026-09-14 | 요청·승인·거부, 정책 snapshot, 멱등성과 감사 타임라인 |
| M3 — Argo Rollouts 제어 | 2주 | 2026-09-14 | Canary 시작·관측·승격·중단과 실클러스터 검증 |
| M4 — 지표 분석과 자동 판정 | 2주 | 2026-09-14 | Prometheus Worker, 정책 gate, PASS/FAIL/INCONCLUSIVE 제어 |
| M5 — 관측성과 운영 UI | 1.5주 | 2026-09-14 | 역할별 Control Room, 로그·메트릭·trace와 감사 UI |
| M6 — 공개 데모와 AWS | 1.5주 | 2026-09-14 | EKS Auto Mode, DNS·TLS, Argo CD, digest GitOps |
| M7 — 포트폴리오 마감 | 1주 | 2026-09-14 | 시연·운영 문서, 장애 경로와 복구 검증 |
| 후속 안정화 1~177 | 지속 개선 | 2026-09-15~18 | 보안, 동시성, timeout, 접근성, 다중 탭·지연 응답 격리 |
| 공개 쇼케이스·증거 178~192 | 지속 개선 | 2026-09-18~19 | Progressive Delivery 시각화, 감사 체인·변조 탐지·JSON 보고서 |
| GitHub Checks 운영 연결 | 배포 설정 | 2026-09-19 | GitHub App 최소 권한, 자동 token 갱신, 실 Check Run 검증 |

초기 예상은 순차적인 10주 개발 계획이었지만, vertical slice 단위 구현과 자동화된 CI/CD 검증을 병렬화해 6일간의 집중 작업으로 완료했다. 이 기간은 기능 개발 기록을 뜻하며, 실제 조직 도입에 필요한 보안 심사·부하 시험·재해 복구 훈련 기간은 포함하지 않는다.

## 단계별 결과

### 1. 제품 기반

- Java 21 Spring Boot Control Plane, Python Analysis Worker, Next.js Web Console을 하나의 저장소로 구성했다.
- OpenAPI, 정책 JSON Schema, observability query template을 코드와 함께 버전 관리한다.
- H2 단위·통합 검사와 임시 MySQL 8.4 기반 실제 migration·복원 훈련을 CI에 연결했다.

### 2. 안전한 릴리스 제어

- Developer 요청, Approver 승인·거부, Operator Rollout 제어를 역할별로 분리했다.
- 요청·승인·Rollout 실행 시 Environment, Cluster, Prometheus 상태와 검증 만료를 fail-closed로 확인한다.
- Canary와 Blue/Green, route별 정책, 자동 PAUSE·PROMOTE·ABORT를 지원한다.

### 3. 운영 신뢰성

- 모든 mutation에 CSRF, 멱등 키, 동시성 잠금과 대상 세대 검사를 적용했다.
- timeout, 응답 유실, 지연 응답, 다중 탭 경쟁 이후 안전한 재시도와 focus 복구를 검증했다.
- 감사 이벤트를 SHA-256 체인으로 연결하고 외부 S3 Object Lock 보관 실패를 재시도한다.

### 4. 공개 경험과 증거

- `/showcase`에서 승인, 단계별 Canary, 오류 주입, 자동 롤백과 복구를 재현할 수 있다.
- 감사 레코드의 expected/actual hash, 최초 불일치와 후속 손상 범위를 보여준다.
- 정상·변조 상태를 `releasepilot.evidence-integrity/v1` JSON 보고서로 내보낼 수 있다.

### 5. GitHub Checks

- GitHub App `ReleasePilot Checks automaster5013`을 `automaster5013/ReleasePilot` 저장소에만 설치했다.
- 권한은 `Checks: write`와 GitHub 필수 `Metadata: read`로 제한했다.
- private key는 Git에 저장하지 않고 EKS Secret의 projected volume으로 전달한다.
- Control Plane이 RS256 JWT를 서명해 1시간 installation token을 발급하고 만료 5분 전에 갱신한다.
- 운영 worker가 Check Run `105735510576`을 생성·갱신해 `GitHub App connection verified` 성공 상태를 게시했다.

## 배포와 검증 흐름

```text
코드·문서 변경
  → CI: 테스트 / 계약 / 브라우저 / secret scan
  → CD: 변경 이미지 build / 취약점 / provenance / push
  → GitOps digest commit
  → Argo CD sync
  → Canary 20% 자동 관찰
  → 50% 신규 Pod 직접 readiness·로그·기능 검증
  → 수동 stable 승격
  → Rollout 2/2 Healthy + 공개 HTTPS smoke test
```

GitHub Checks 최종 통합에서는 다음 증거를 확인했다.

| 검증 | 결과 |
| --- | --- |
| Control Plane 전체 테스트 | 205개 성공 |
| Secret scan | gitleaks 통과 |
| 소스 CI | #369 성공 |
| 이미지·GitOps CD | #124 성공 |
| 최종 문서 CI | #370, #371 성공 |
| EKS Rollout | 2/2 Ready, Healthy |
| Argo CD | Synced, Healthy |
| 공개 readiness | HTTP 200 |
| GitHub App E2E | Check Run 생성·갱신 성공 |
| 키 정리 | 교체 키 2개 폐기, 로컬 PEM 3개 제거, EKS 최종 키 1개 유지 |

## 이후 운영 계획

현재 개발 백로그는 완료됐지만, 실제 프로덕션 전환 시 다음 활동을 별도 일정으로 잡아야 한다.

| 우선순위 | 작업 | 완료 기준 |
| --- | --- | --- |
| P0 | EKS 내부 MySQL을 RDS Multi-AZ로 이전 | 복구 훈련, 암호화, PITR, 장애 조치 검증 |
| P0 | 전용 secret manager와 자동 key rotation | GitHub App·클러스터·Prometheus 자격 증명 무중단 교체 |
| P1 | WAF와 인증된 관측성 endpoint | 공개 공격면·rate limit·운영 접근 정책 검증 |
| P1 | 조직별 OIDC/SSO 공급자 설정 | 실제 IdP login/logout와 계정 lifecycle 인수 테스트 |
| P1 | 부하·장애·재해 복구 훈련 | 목표 RTO/RPO와 대규모 동시 릴리스 기준 충족 |
| P2 | 비용·용량 장기 관찰 | EKS, 로그, S3 보존 비용과 autoscaling 기준 확정 |

## 관련 문서

- [저장소 메인 README](../../README.md)
- [MVP 실행 백로그](mvp-backlog.md)
- [시스템 아키텍처](../architecture/system-architecture.md)
- [공개 데모 Runbook](../runbooks/public-demo.md)
- [GitHub Checks 연동](../integrations/github-checks.md)
