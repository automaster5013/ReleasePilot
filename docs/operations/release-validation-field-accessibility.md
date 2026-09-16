# 릴리스 요청 필드 오류 연결·포커스 접근성 검증

## 검증 범위

릴리스 요청의 클라이언트 검증이 실패하면 첫 번째 잘못된 값을 필드와 함께 반환한다. 화면은 해당 입력에 `aria-invalid="true"`와 `aria-errormessage="release-request-error"`를 부여하고, 동일 ID의 assertive 오류 alert를 렌더링한 뒤 해당 필드로 포커스를 이동한다. 사용자가 문제 필드를 수정하면 오래된 필드 오류 연결을 제거한다.

Service, Environment, Version, Image repository, Image digest, Change summary, Commit SHA, Pipeline URL, 선택적인 Policy Version ID 전체를 이 계약에 포함했다. DEVELOPER, APPROVER, OPERATOR fixture에서 유효한 요청을 채운 뒤 Policy Version ID만 잘못 입력하여 오류 alert 연결, invalid 상태, 포커스 이동, mutation 미전송을 브라우저별로 검증했다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 153개 통과
- 로컬 WebKit 접근성 59개 통과
- GitHub Actions CI `35109889616`: Chromium 153개, Firefox 접근성 59개, WebKit 접근성 59개 등 총 271개 통과

## 운영 반영

Docker Hub CD `35109890055`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `5b65506`의 digest `sha256:b44d8071323a6709323870631b753d4234d88d714f1ceb45bd2ce19ce532fd2e`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 CSP 및 HSTS를 확인했다.
