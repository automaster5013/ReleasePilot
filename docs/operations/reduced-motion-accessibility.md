# 동작 감소 접근성 검증

ReleasePilot Web Console은 운영체제나 브라우저에서 `prefers-reduced-motion: reduce`를 선택한
사용자에게 부드러운 스크롤과 지속 시간이 긴 애니메이션·전환 효과를 적용하지 않는다.

## 구현과 검증

- 기본 화면의 부드러운 스크롤은 일반 사용자에게 유지한다.
- 동작 감소 설정에서는 문서와 모든 요소의 스크롤을 즉시 이동으로 바꾼다.
- 애니메이션 반복을 한 번으로 제한하고 애니메이션·전환 지속 시간을 0.01ms로 줄인다.
- VIEWER, DEVELOPER, APPROVER, OPERATOR의 실제 렌더 트리를 검사해 설정 인식, 루트
  `scroll-behavior: auto`, 0.01ms를 넘는 활성 애니메이션·전환 효과가 없음을 확인한다.

로컬에서 단위 테스트 35개, lint, typecheck, production build와 Chromium 114개,
WebKit 접근성 20개 등 총 134개 브라우저 시나리오가 통과했다. GitHub Actions CI
`35076012341`에서는 Chromium 114개와 Firefox·WebKit 접근성 각 20개, 총 154개를 통과했다.

## 운영 반영

- 소스 commit `bda3a41`에 동작 감소 스타일과 네 역할의 회귀 검사를 반영했다.
- Docker Hub CD `35076012547`은 Web Console 단일 이미지의 취약점 검사, provenance 생성·검증,
  GitOps 갱신과 배포 경계 검증을 통과했다.
- GitOps commit `a05eda5`가 digest
  `sha256:39b5c394bafa12aceedfe00c83fc3bb8949b5fa956c644b924eda8c486d82f31`을 반영했다.
- Sigstore admission과 Canary 검증 후 새 digest를 최종 stable로 승격했다.
- 세 Rollout은 2/2 Healthy, 모든 Pod는 Ready이고 재시작 횟수는 0이다.
- 네 Argo CD 애플리케이션은 Synced/Healthy이며 공개 루트, `/health`,
  `/actuator/health/readiness`는 HTTP 200을 반환한다.

이 검사는 CSS 기반 애니메이션과 전환을 다룬다. 브라우저 자체 UI, 운영체제 효과와 향후 추가될
영상 콘텐츠는 별도의 동작 감소 제어가 필요하다.
