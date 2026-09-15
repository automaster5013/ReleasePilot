# v0.53.0 저장 매핑·감사 정밀도 배포 검증

2026-09-15

## 대상

JSON JDBC 매핑 6개 필드와 감사 이벤트 생성 시각의 마이크로초 정규화를 반영한다. 분석 DB/Outbox 통합 및 감사 DB 왕복 검증을 포함한다. 기존 운영 데이터 변환·감사 기록 복구는 하지 않는다.

- 소스: `v0.53.0` (`1df2a80`)
- [소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34953566305): success
- [이미지 및 GitOps workflow](https://github.com/automaster5013/ReleasePilot/actions/runs/34953887943): success
- 로컬: 서버 전체 149개, MySQL 분석/감사 11개 통과

## 배포 상태

GitOps revision `5b4300987e1ffe49974e53ac27a5e40c7377a9f8`에서 Argo CD Synced/Healthy를 확인했다. 기존 `visionflow-admin` IAM 사용자로 클러스터에 접근했으며 권한/인증 설정을 변경하지 않았다.

Canary 3개에서 이미지 digest 일치, readiness, 재시작 0회, 최근 10분 ERROR/Exception/Traceback 표본 0건과 공개 헬스 200을 확인했다. 자동 60초 대기 후 50% 수동 대기(step 3)에서 승격했다. 로컬 CLI 플러그인이 없어 Kubernetes Rollout status의 pauseConditions를 해제했다. 최종 세 Rollout 모두 Healthy, ready/updated/available 각각 2, 새 Pod 6개 모두 ready·digest 일치·재시작 0회다.

승격 후 로그 표본에서 control-plane 경고 1건은 아래 권한 검사의 최초 요청 본문 누락에 따른 `Required request body is missing`으로 확인했다. 나머지 Pod 표본에는 ERROR/Exception/Traceback이 없었다. 표본 검사는 무오류 운영 전체를 증명하지 않는다.

## 공개 검증

인증서를 검증하는 HTTPS curl에 기존 Ingress IP `43.200.251.24`를 `--resolve`로 지정했다. TLS 검증을 생략하지 않았다.

- health/actuator health/liveness/readiness: 200
- 공개 actuator prometheus/info: 404
- session providers: 200
- 페이지 script 9개의 nonce 일치, unsafe-eval 없음, HSTS/no-store 유지
- 데모 로그인/세션 복원: 200, VIEWER
- 릴리스 목록: 200, items 0개
- VIEWER의 all-zero UUID Abort: 최초 본문 누락 400 이후 유효한 JSON 본문·CSRF·Idempotency 헤더로 403
- 검사 세션 로그아웃: 204

쿠키/CSRF는 메모리에서만 사용했다. 실제 릴리스 조작이나 운영 데이터 복구/변환은 하지 않았다. SSO/GitHub Checks 보류와 데이터 부재에 따른 실제 운영 릴리스 E2E 미검증 범위는 유지한다. 운영 감사 체인의 기존 기록 무결성은 이번 공개 smoke 검사로 증명하지 않는다.
