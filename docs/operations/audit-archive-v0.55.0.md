# v0.55.0 감사 아카이브 안전성 배포

2026-09-15

## 대상 및 근거

예외 메시지를 전달 상태에 저장하지 않는 고정 코드 `AUDIT_ARCHIVE_UNAVAILABLE`와 9번째 실패부터 300초 재시도 지연 상한을 반영한다. 기존 운영 오류 정리나 감사 데이터 복구는 포함하지 않는다.

- 소스 `14bf935fe2f68c8f8977773f0a13b70d28b54e36`, 태그 `v0.55.0`
- [소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34956717534): success
- [이미지 및 GitOps workflow](https://github.com/automaster5013/ReleasePilot/actions/runs/34956878766): success
- 로컬 서버 전체 171개 및 MySQL 통합 21개 통과

## 진행 상태

기존 `visionflow-admin` IAM 사용자 인증을 사용했다. GitOps revision `47ab94d05ac0de696a593a968082a837c07e250d`에 Argo CD Synced/Healthy를 확인했다.

새 Pod 배치가 기존 노드의 `karpenter.sh/disrupted:NoSchedule` taint로 잠시 대기했다. 자동 관리가 해제한 뒤 배치됐으며 taint/toleration/인프라 설정을 변경하지 않았다. 새 Canary 3개 ready·digest 일치·재시작 0회·최근 10분 ERROR/Exception/Traceback 표본 0건과 공개 헬스 200을 확인했다. 자동 대기 뒤 세 Rollout 모두 step 3의 50% 수동 대기임을 확인하고 Kubernetes status pauseConditions를 해제해 승격했다.

최종 세 Rollout 모두 Healthy, ready/updated/available 각각 2다. 새 Pod 6개 모두 ready·digest 일치·재시작 0회, 최근 10분 오류 표본 0건이다. 표본 검사는 무오류 운영 전체를 증명하지 않는다.

## 공개 검증

기존 Ingress IP `43.200.251.24`를 HTTPS curl의 `--resolve`로 지정하고 인증서 검증을 유지했다.

- health/actuator health/liveness/readiness 200
- 공개 actuator prometheus/info 404
- session providers 200
- 페이지 script 9개 nonce 일치, unsafe-eval 없음, HSTS/no-store 유지
- 데모 로그인/동일 세션 복원 200, VIEWER
- 릴리스 목록 200, items 0개
- 유효한 JSON·CSRF·Idempotency를 사용한 VIEWER all-zero UUID Abort 403
- 검사 세션 로그아웃 204

쿠키/CSRF는 메모리에서만 사용했다. 실제 릴리스 mutation·운영 감사 변조/삭제·외부 실패 주입은 하지 않았다. SSO/GitHub Checks 보류를 유지한다. 실제 운영 아카이브 실패/복구, 기존 감사 기록 무결성, 데이터 부재에 따른 전체 릴리스 E2E는 이번 공개 smoke로 증명하지 않는다.
