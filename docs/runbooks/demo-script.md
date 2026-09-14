# ReleasePilot 3~5분 데모 스크립트

## 0:00–0:40 — 문제와 경계

`https://releasepilot.kr`을 열고 ReleasePilot이 Kubernetes를 대체하지 않으며, Argo Rollouts를 실행
엔진으로 사용하면서 승인·정책 판정·감사 증거를 관리한다고 설명한다.

## 0:40–1:30 — 읽기 전용 공개 세션

`읽기 전용 데모`를 누른다. 연결 상태가 `DEMO · VIEW ONLY`로 바뀌고 Promote, Pause, Abort가 계속
비활성인지 보여준다. 이 세션은 VIEWER이고 demo Project 밖의 데이터와 상태 변경 API에는 접근할 수 없다.

## 1:30–2:30 — Canary 진행

10→30→60→100 단계, 현재 상태와 최근 판정을 설명한다. 정상 릴리스에서는 각 관측 창의 요청 수,
5xx 비율과 p95 지연 시간이 정책을 통과한 뒤에만 다음 단계로 진행한다.

## 2:30–3:30 — 실패 안전성과 증거

Decision Evidence에서 stable/canary 값, threshold, verdict와 query hash를 짚는다. FAIL이면 Argo
Rollouts에 abort를 요청하고 stable 복구를 관측한 뒤에만 ROLLED_BACK으로 기록한다. 표본 부족이나
Prometheus 장애는 INCONCLUSIVE로 처리해 자동 승격하지 않는다.

## 3:30–4:30 — 재현성과 운영

Git 태그가 세 이미지를 빌드하고 ECR digest를 GitOps overlay에 기록하며 Argo CD가 동기화하는 흐름을
보여준다. 마지막으로 correlation ID, 감사 이벤트, runbook을 통해 판정과 운영 조치를 재현할 수 있음을
설명한다.

## 종료 확인

`/health`가 200인지, 인증서가 유효한지, Argo CD가 Synced/Healthy인지 확인한다. 공개 데모에서는
클러스터·Grafana·관리 계정 정보를 노출하지 않는다.
