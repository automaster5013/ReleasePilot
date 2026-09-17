# 세션 로그아웃 동시 실행 잠금 검증

## 검증 범위

상단의 일반 로그아웃과 조직 SSO 계정 변경이 React 비활성화 렌더 전에 연속 실행되더라도 두 번째 CSRF 조회·로그아웃 mutation·SSO 이동을 시작하지 않도록 기존 공유 동기 잠금의 동작을 회귀 테스트로 고정했다. 실패 후에는 잠금과 busy 상태를 해제해 사용자가 안전하게 재시도할 수 있다.

APPROVER fixture에서 로그아웃 응답을 지연시키고 같은 JavaScript task 안에서 일반 로그아웃을 두 번 실행한 뒤 계정 변경까지 실행했다. 서버에는 CSRF로 보호된 로그아웃 POST가 정확히 1회만 도달하고, 대기 중 교차 SSO 이동은 발생하지 않는지 검증했다. CI 병렬 부하에서 세션·공급자 초기 렌더를 기다리지 않던 최초 테스트 실패도 분석해 두 제어의 표시를 명시적으로 기다리도록 안정화했으며, 9개 병렬 worker에서 10회 반복 통과를 확인했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 178개 및 대상 테스트 병렬 10회 반복 통과
- GitHub Actions CI `35204044319`: Chromium 178개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 324개 통과
- 최초 Docker Hub CD `35203401738`은 테스트 초기화 경합을 감지해 게시 전에 차단했고, 안정화 후 재실행에서 같은 독립 검증 전체를 통과했다.

## 운영 반영

Docker Hub CD `35204044477`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `4906350`의 digest `sha256:e522ad612899aa1833697401aef4ae6b3512fcf84df39158a3fc4a8d061367ae`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. Argo CD Application은 Synced·Healthy였으며 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
