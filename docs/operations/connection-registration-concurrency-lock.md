# 외부 연결 등록 동시 실행 잠금 검증

## 검증 범위

Kubernetes 클러스터와 Prometheus 연결 등록이 React 비활성화 렌더 전에 연속 제출되더라도 두 번째 mutation을 시작하지 않도록 두 등록 경로가 공유하는 동기 잠금을 적용했다. 필드 검증을 통과한 뒤 잠금을 획득하므로 잘못된 입력은 다른 등록을 막지 않으며, 첫 요청이 성공하거나 실패한 뒤 잠금을 해제해 운영자의 재시도 경로를 유지한다.

OPERATOR fixture에서 Kubernetes 등록 응답을 지연시키고 같은 JavaScript task 안에서 해당 form의 submit 이벤트를 두 번 전달한 뒤, 첫 요청이 대기 중인 상태에서 Prometheus form도 제출했다. React busy 상태 렌더 여부와 관계없이 유효한 Kubernetes 등록 POST와 본문만 정확히 1회 전송되고 중복·교차 등록 mutation은 시작되지 않는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 175개 통과
- 로컬 WebKit은 Windows Playwright 런타임이 모든 브라우저 프로세스를 시작 직후 종료해 실행 환경 장애로 판정했으며, 코드 실패 여부는 깨끗한 GitHub Linux 러너에서 재검증했다.
- GitHub Actions CI `35196477418`: Chromium 175개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 321개 통과

## 운영 반영

Docker Hub CD `35196477630`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `67c9e5c`의 digest `sha256:a1bce976d841e43658af81b4696ee2b55a58878fc49e37fb8519e6debd8963ae`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. Argo CD Application은 Synced·Healthy였으며 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
