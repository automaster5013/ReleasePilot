# 외부 연결 검증 동시 실행 잠금 검증

## 검증 범위

Kubernetes·Prometheus의 개별 연결 검증과 종류별 일괄 검증이 React 비활성화 렌더 전에 연속 실행되더라도 두 번째 mutation을 시작하지 않도록 네 검증 경로가 공유하는 동기 잠금을 적용했다. 첫 요청이 성공하거나 실패한 뒤에만 잠금을 해제하므로 오류 이후 운영자의 수동 재시도 경로는 유지한다.

OPERATOR fixture에서 Kubernetes 개별 검증 응답을 지연시키고 같은 JavaScript task 안에서 검증 버튼 이벤트를 두 번 전달한 뒤, 첫 요청이 대기 중인 상태에서 Prometheus 일괄 검증 이벤트도 전달했다. React busy 상태 렌더 여부와 관계없이 선택한 Kubernetes 연결의 검증 POST만 정확히 1회 전송되고 교차 일괄 검증 mutation은 시작되지 않는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 174개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35194515008`: Chromium 174개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 320개 통과
- 릴리스 조회 오류 접근성 fixture는 대상 릴리스를 명시적으로 선택한 뒤 제출하도록 보강해 WebKit hydration 타이밍 의존성을 제거했다.

## 운영 반영

Docker Hub CD `35194515267`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `0dd899e`의 digest `sha256:70e7cbaa333b4ed4abc58bd5f3b484a77f4f7415a94709d5bbbaec30a0b89a92`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
