# v0.54.0 감사 검증 보강 배포

2026-09-15

## 범위

감사 verifier의 head 잠금과 끝값 비교, 부분 해시 필드 누락 거부, 성공적으로 검증한 이벤트 수 집계를 반영한다. legacy 제외 정책과 API 필드 형태는 유지한다. 운영 기존 감사 기록 복구·변환이나 외부 연동 활성화는 포함하지 않는다.

- 소스 `d119a16c38791b755e214aa10b65b5fd89b8ed7c`, 태그 `v0.54.0`: 서버 전체 157개 및 MySQL 통합 19개 2회 통과
- [소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34955162801): success (6개 job 모두 성공)
- [이미지 및 GitOps workflow](https://github.com/automaster5013/ReleasePilot/actions/runs/34955442238): success
- [검사 구현 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34954814731): success

## 진행 상태

GitOps revision `81d4d7b0324de6e1d1ad4ec0c14a6e52c1512363`에 Argo CD Synced/Healthy를 확인했다. 기존 `visionflow-admin` IAM 사용자 인증을 사용했고 인증/권한/인프라 설정은 바꾸지 않았다.

배치 초기에 기존 노드의 `karpenter.sh/disrupted:NoSchedule` taint로 새 Canary 3개가 Pending이었다. 기존 노드는 Ready이고 메모리/디스크 압력은 없었다. 자동 관리 과정에서 taint가 해제된 후 모두 배치됐으며 수동 taint 제거나 toleration 변경은 하지 않았다.

새 Canary 3개 digest 일치, ready, 재시작 0회, 최근 10분 ERROR/Exception/Traceback 표본 0건과 공개 헬스 200을 확인했다. 자동 60초 대기 뒤 세 Rollout 모두 step 3의 50% 수동 대기인 것을 확인하고 Kubernetes status pauseConditions를 해제했다. 최종 세 Rollout의 ready/updated/available은 각각 2이고 Healthy다. 새 Pod 6개 모두 ready·digest 일치·재시작 0회다.

승격 후 웹 Pod 1개에서 `The Server Reference ID did not match the expected format. Received "y".` 오류 표본 1건이 발견됐다. 해당 요청의 출처는 확인하지 못했다. 다른 Pod 5개 표본에는 ERROR/Exception/Traceback이 없었다. 이후 페이지/헬스/providers 200과 웹 Pod 2개의 readiness/재시작 0회는 재확인했다. 무오류 운영 전체를 증명하는 것은 아니다.

## 공개 검증

기존 Ingress IP `43.200.251.24`를 curl `--resolve`에 지정하고 HTTPS 인증서 검증을 유지했다.

- health/actuator health/liveness/readiness 200
- actuator prometheus/info 공개 노출 404
- session providers 200
- 페이지 script 9개 nonce 일치, unsafe-eval 없음, HSTS/no-store 유지
- 데모 로그인/동일 세션 복원 200, VIEWER
- 릴리스 목록 200, items 0개
- 유효한 JSON 본문·CSRF·Idempotency를 사용한 VIEWER all-zero UUID Abort 403
- 검사 세션 로그아웃 204

쿠키/CSRF는 메모리에서만 사용했다. 실제 릴리스 mutation은 하지 않았다. SSO/GitHub Checks 보류는 유지한다. 실제 운영 릴리스 E2E와 운영 기존 감사 체인의 무결성 확인/복구는 이번 공개 smoke 검증에 포함하지 않는다.
