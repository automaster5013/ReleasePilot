# v0.51.0 보안 보강 배포 검증

2026-09-15

## 반영 범위

- SecretMaterial 문자열 출력의 고정 마스킹
- Jackson 자동 JSON 출력에서 bearer token 제외
- 분석 Worker 예외 원문 대신 고정 재시도 오류 코드 저장
- 설정된 분석 자격 증명 누락·공백·조회 예외에서 Worker 호출 차단 및 최종 PAUSE

릴리스 소스는 `106478b5d40f231d62e81a4412339f1c663c0cb9`, 태그는 `v0.51.0`이다. 추가 경계/복구 테스트는 `b0cc7c4`이며 런타임 코드를 바꾸지 않는다. 이미지 digest를 반영한 GitOps revision `5a88efa02c9078c6ef56d5d7cca6c9fa7937b994`가 실제 Argo CD에서 Synced/Healthy임을 확인했다.

## 자동 검증

- [릴리스 소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34946669136): success
- [이미지 빌드 및 GitOps 갱신](https://github.com/automaster5013/ReleasePilot/actions/runs/34946671136): success
- [추가 테스트 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34946809461): success
- 로컬 서버 전체 verify: 100개 테스트, 실패/오류/건너뜀 0

추가 테스트는 null/빈 문자열/공백/탭·개행 토큰과 다음 예약 시도에서 자격 증명을 다시 읽어 PASS/PROMOTE로 복구되는 경로를 검증한다. Worker/저장소 mock 기반이므로 실제 운영 분석 성공을 증명하는 테스트는 아니다.

## 실제 배포

20% 및 60초 대기 후 50% 수동 대기 단계에서 새 Pod readiness, 재시작 0회, 이미지 digest 일치 및 최근 5분 ERROR/Exception/Traceback 표본 0건을 확인하고 수동 승격했다. 최종 Control Plane/Analysis Worker/Web Console은 모두 Healthy, ready/updated/available 각각 2다. 활성 새 Pod 6개 모두 ready, 재시작 0회다.

## 공개 HTTPS 표본

인증서 검증을 유지한 curl 요청으로 확인했다. DNS 응답이 불안정하여 기존에 확인한 Ingress IP를 `--resolve`로 지정했다. TLS 검증을 생략하지 않았다.

| 검사 | 결과 |
|---|---|
| `/health` 및 actuator health/liveness/readiness | 모두 200 |
| `/actuator/prometheus`, `/actuator/info` | 모두 404 |
| `/control-api/session/providers` | 200 |
| 데모 로그인 | 200, VIEWER, demo=true |
| 동일 세션 복원 | 200, VIEWER |
| 릴리스 목록 | 200, 0개 |
| VIEWER Abort 권한 검사 | 403 |
| 검사 세션 로그아웃 | 204 |

Abort 검사는 실제 릴리스가 아닌 all-zero UUID에 유효한 CSRF/Idempotency 헤더를 사용했다. OPERATOR 전용 메서드의 권한 거부를 확인하는 검사이며, 실제 릴리스 조작이나 Rollout 변경을 하지 않았다. 세션 쿠키와 CSRF 값은 메모리에서만 사용하고 출력·파일 저장하지 않았다.

## 남은 범위

실제 릴리스 상세/분석/복구 E2E는 공개 목록에 데이터가 없어 검증하지 못했다. SSO/GitHub Checks는 사용자 요청대로 보류했다. Secrets Manager/Vault 활성화, 자격 증명 자동 교체, 관리형 DB 및 운영 백업 복구는 수행하지 않았다. 이 보고서는 네 가지 코드 보강의 배포와 공개 경계 표본 검증에 한정된다.
