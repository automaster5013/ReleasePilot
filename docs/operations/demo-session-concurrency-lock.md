# 공개 데모 세션 시작 동시 실행 잠금 검증

## 검증 범위

공개 데모 진입 버튼의 React 비활성화 렌더 이전에 같은 JavaScript task에서 중복 활성화가 발생해도 두 번째 CSRF 조회와 데모 세션 생성을 시작하지 않도록 동기 잠금을 적용했다. 첫 요청이 성공하거나 실패한 뒤에만 잠금을 해제하므로 실패 후 수동 재시도 경로는 유지한다.

VIEWER fixture에서 CSRF 응답을 지연시키고 같은 task 안에서 클릭 이벤트를 두 번 연속 전달했다. React busy 상태 렌더 여부와 무관하게 CSRF 요청이 1회로 유지되고, 잠금 해제 전 데모 mutation이 시작되지 않으며, 응답 재개 후 데모 mutation도 정확히 1회만 전송되는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 172개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35174138827`: Chromium 172개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 318개 통과

## 운영 반영

Docker Hub CD `35174138917`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `9688906`의 digest `sha256:9d44861133a06c7924c894603f518a82397ac6ecf55e863da3dd3d57f3c7204b`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
