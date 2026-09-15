# 예약된 승격 명령의 실행 전 readiness

2026-09-15

분석 PASS 처리 이후 Outbox의 PROMOTE_ROLLOUT 실행까지 연결 상태가 달라질 수 있다. 실제 명령 처리 경로는 승격을 실행하기 전에 기존 START_ROLLOUT readiness 검사를 재사용한다. 자동 및 운영자 승격에 동일하게 적용한다.

Environment는 ACTIVE/ACTIVE_WITH_WARNINGS 및 최근 검증, 실행 대상 Kubernetes 연결과 현재 Environment의 Prometheus 연결은 ACTIVE 및 최근 검증이어야 한다. 환경/연결별 기존 max-age 설정을 유지하며 정확히 유효기간이 경과한 검증도 차단한다. 검사가 실패하면 비밀 조회와 외부 Argo 제어 호출 전에 안정적인 기존 오류 코드로 실패한다. Step을 통과 상태로 변경하거나 성공 감사를 생성하지 않는다.

기존 Outbox 재시도 처리를 유지한다. FAILED 명령은 최대 300초 지연으로 다시 조회되며 현재 최대 시도 제한은 없다. 연결을 복구·재검증한 후 동일 명령은 실행할 수 있다. readiness 실패를 Rollout 일시정지 성공으로 표시하지 않으며 이 변경 자체가 별도 PAUSE 명령을 생성하지도 않는다. 재시도 제한/명령 취소 정책은 별도 작업이다.

PAUSE/ABORT는 위험을 줄이는 조작이므로 이 readiness gate를 적용하지 않는다. 기존 Kubernetes 자격 증명 및 UID 제어 조건은 그대로 필요하다. RESUME 경로는 이 작업에서 변경하지 않았다.

회귀 사례 13개는 환경/연결 비활성·정확한 만료·검증 시각 누락 차단 9개, 정상 승격과 감사, 동일 예약 명령 복구, 비활성 Prometheus에서도 PAUSE/ABORT 유지 2개를 포함한다. 실제 도메인 객체와 mock gateway/저장소를 사용하며 운영 Rollout을 조작하지 않는다.

로컬 서버 전체 `mvnw --batch-mode verify`에서 138개 테스트가 실패·오류·건너뜀 없이 통과했다.

조회와 외부 mutation 사이의 다른 트랜잭션을 전역 직렬화하지 않는다. 분석 시점의 연결 ID/전체 설정 snapshot을 Outbox에 추가한 변경도 아니다. 공개 배포 이미지는 변경하지 않았다.
