# 오류 후 조작 포커스 복구 접근성 검증

## 검증 범위

비동기 조작 중 트리거 버튼이 비활성화되면 브라우저가 키보드 포커스를 문서 본문으로 떨어뜨릴 수 있다. ReleasePilot은 데모 로그인, 로그아웃, 릴리스 요청, 승인·거부, 운영 조작의 실패 시 원래 트리거 요소를 보관하고 React의 busy 상태 해제 렌더링이 끝난 뒤 해당 요소가 여전히 연결되고 활성화된 경우 포커스를 복구한다.

VIEWER, DEVELOPER, APPROVER, OPERATOR에서 키보드 Enter로 조작한 뒤 잘못된 JSON 또는 CSRF 응답을 발생시켜 assertive 오류 alert가 표시되고, 서버 mutation이 전송되지 않으며, 포커스가 원래 버튼으로 돌아오는지 검사한다. 이 회귀 검사는 오류 후 키보드 사용자가 현재 작업 위치를 잃지 않도록 WCAG 2.2 성공 기준 2.4.3 Focus Order와 3.2.1 On Focus의 조작 실패 경계를 보호한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 150개 통과
- 로컬 WebKit 접근성 56개 통과
- GitHub Actions CI `35107400501`: Chromium 150개, Firefox 접근성 56개, WebKit 접근성 56개 등 총 262개 통과

## 운영 반영

Docker Hub CD `35107401090`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `6958c7b`의 digest `sha256:e3d341cf27a40740219000c52e91895c7155a642a0327b5550fb30ed76f52804`를 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 Ready이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 CSP 및 HSTS를 확인했다.
