# 외부 연결 변경 동시 실행 잠금 검증

## 검증 범위

Kubernetes·Prometheus 연결 편집과 활성화·비활성화가 React 비활성화 렌더 전에 연속 실행되더라도 두 번째 mutation을 시작하지 않도록 세 변경 경로가 공유하는 동기 잠금을 적용했다. 잠금은 편집 prompt와 비활성화 확인 대화상자보다 먼저 획득해 대화상자가 열린 동안의 중복·교차 조작도 차단한다. 사용자가 취소하거나 입력 검증이 실패하면 즉시 잠금을 해제하고, 서버 요청은 성공·실패 후 해제해 수동 재시도를 유지한다.

OPERATOR fixture에서 비활성 Prometheus 연결의 재활성화 응답을 지연시키고 같은 JavaScript task 안에서 버튼을 두 번 활성화한 뒤, 첫 요청이 대기 중인 상태에서 Kubernetes 편집도 실행했다. React busy 상태 렌더 여부와 관계없이 Prometheus 재활성화 POST만 정확히 1회 전송되고 중복 상태 변경과 교차 편집 mutation은 시작되지 않는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 176개 통과
- 로컬 WebKit은 Windows Playwright 런타임이 단일 브라우저 프로세스도 시작 직후 종료해 실행 환경 장애로 판정했으며, 깨끗한 GitHub Linux 러너에서 재검증했다.
- GitHub Actions CI `35198872894`: Chromium 176개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 322개 통과

## 운영 반영

Docker Hub CD `35198873228`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `80e30da`의 digest `sha256:b13bb95117d4631657f3711d821f310cc86ea0ee7b3c119a4c5161e26ab6271a`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. Argo CD Application은 Synced·Healthy였으며 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
