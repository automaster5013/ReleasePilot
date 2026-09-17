# 세션 로그아웃 timeout 복구

## 적용 범위

일반 로그아웃과 조직 SSO 계정 변경이 공유하는 세션 종료 POST에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 응답이 멈추면 요청을 중단하고 재시도 가능한 한국어 오류를 알린 뒤 공유 로그아웃 잠금과 busy 상태를 해제한다. 오류를 발생시킨 제어로 포커스를 복원하므로 사용자는 로그아웃 또는 계정 변경을 즉시 다시 시도할 수 있다.

## 회귀 검증

- 첫 로그아웃 응답을 정지시켜 로그아웃과 계정 변경 제어가 잠겼다가 timeout 후 함께 복구되는지 확인했다.
- 같은 로그아웃의 두 번째 시도가 성공하고 CSRF로 보호된 POST가 정확히 두 번만 전송되는지 검증했다.
- 대상 시나리오는 10 worker 병렬 반복 10회에 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 192개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35237595599`(#291)는 6분 7초에 성공했다. Chromium 192개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 338개를 검증했다.

## 운영 반영

Docker Hub CD `35237595979`(#85)은 변경된 web-console만 게시하고 7분 58초에 성공했다. GitOps commit `fb6fd0a`은 web-console을 `sha256:4a82658c527e794534e57d70563cb60fb21b3790c52216ee88edf538f6f0f611`로 갱신했다.

Canary는 자동 20% 관찰 단계를 통과한 뒤 50%에서 신규·기존 Pod Ready, 재시작 0, 정확한 digest와 공개 root·`/health` HTTPS 200을 확인하고 100%로 수동 승격했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이며 최근 10분 오류 표본은 0건이다. 전체 운영 Pod 재시작은 0이고 sample-checkout은 1/1 Healthy, 네 Argo CD Application은 Synced/Healthy다. root의 CSP와 HSTS, health의 HSTS도 유지된다.

요청이 서버에 도달한 뒤 응답만 유실되거나 여러 탭에서 동시에 로그아웃하는 경우의 중복 효과는 서버 세션 종료의 멱등성으로 방어한다.
