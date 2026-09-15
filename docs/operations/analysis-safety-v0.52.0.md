# v0.52.0 분석 안전성 배포 검증

2026-09-15

## 완료한 단계

- 분석 요청 구성 실패, 분석/PASS Prometheus readiness, 예약 승격 실행 전 readiness 보강 포함
- [구현 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34948841523): success
- [릴리스 소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34949013104): success
- [세 이미지 빌드 및 GitOps 갱신](https://github.com/automaster5013/ReleasePilot/actions/runs/34949017548): success
- 릴리스 소스 `005bb14777073716274f2713c572f3ae7d8a1e90`, 태그 `v0.52.0`
- digest 반영 GitOps revision `e65265151cffbe9438e095381d6d21296836337d`

## 인증 복구와 실제 승격

GitOps 반영 후 로컬 AWS 자격 증명이 만료되어 승격을 보류했다. 첫 재로그인은 root 세션으로 승인되어 프로필 변경을 거부했으며, 이후 기존 `visionflow-admin` IAM 사용자로 재로그인하여 동일 계정/권한의 caller identity를 확인했다. root 자격 증명은 적용하지 않았다.

50% 수동 대기 단계에서 새 Canary 3개의 이미지 digest 일치, readiness, 재시작 0회, 최근 10분 ERROR/Exception/Traceback 표본 0건과 공개 헬스 200을 확인하고 수동 승격했다. 최종 세 Rollout 모두 Healthy, ready/updated/available 각각 2다. 활성 새 Pod 6개 모두 ready, 재시작 0회다. Argo CD는 당시 main revision `00469e38a7769fb8df129c41f114b16cb048c883`에 Synced/Healthy였다. 이 revision은 위 digest 갱신 commit을 포함한다.

## 승격 후 공개 검증

인증서 검증을 유지한 HTTPS curl로 검사했다. 기존에 확인한 Ingress IP를 `--resolve`로 지정했으며 TLS 검증을 생략하지 않았다.

- `/health`, actuator health/liveness/readiness: 모두 200
- 공개 actuator prometheus/info: 모두 404
- `/control-api/session/providers`: 200
- 공개 페이지: HSTS/no-store/CSP 유지, script 9개의 nonce 일치, unsafe-eval 없음
- 데모 로그인/동일 세션 복원: 200, VIEWER
- 릴리스 목록: 200, 0개
- VIEWER의 all-zero UUID Abort 권한 검사: 유효한 CSRF/Idempotency 헤더로 403
- 검사 세션 로그아웃: 204

쿠키/CSRF는 메모리에서만 사용하고 출력·파일 저장하지 않았다. 실제 릴리스나 운영 Rollout에 Abort를 실행하지 않았다.

## 변경하지 않은 범위

IAM 권한이나 클러스터 인증 설정을 변경하지 않았으며 다른 서비스의 자격 증명을 우회 사용하지 않았다. SSO/GitHub Checks 보류는 유지했다. 공개 릴리스 데이터가 없어 실제 릴리스 상세/분석 E2E는 검증하지 못했다. 코드의 실패 경로 검증은 서버 전체 138개 테스트와 CI를 근거로 하며, 실제 운영 실패 주입 검증으로 해석하지 않는다.
