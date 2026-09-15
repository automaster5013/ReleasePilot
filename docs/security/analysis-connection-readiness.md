# 분석 시도와 PASS 예약의 Prometheus readiness

2026-09-15

릴리스 요청·승인·시작에서 검사한 Prometheus 연결 상태는 분석 시점에 달라질 수 있다. 각 분석 시도는 Worker 호출 전에 ACTIVE 상태와 최근 검증 시각을 확인한다. `releasepilot.connection-validation.max-age` 설정을 공유하며 기본값은 6시간이다. 정확히 최대 유효기간이 경과한 검증도 만료로 처리한다.

비활성/미검증/실패 상태는 `PROMETHEUS_CONNECTION_NOT_ACTIVE`, 검증 시각 누락/만료는 `PROMETHEUS_CONNECTION_VALIDATION_STALE`로 처리한다. Worker와 secret resolver를 호출하지 않고 기존 INCONCLUSIVE 재시도 경로를 사용한다. 최대 시도에서는 PAUSE_ROLLOUT을 예약한다. 정상 정책의 추가 관측 지연은 유지한다.

Worker가 PASS를 반환한 경우 결과 저장 트랜잭션 안에서 현재 Environment의 Prometheus 연결 ID, 상태와 검증 신선도를 다시 조회한다. 연결 ID 변경은 `PROMETHEUS_CONNECTION_CHANGED`로 처리한다. 실패하면 PASS 및 이전 근거를 INCONCLUSIVE/빈 근거로 낮추고 PROMOTE를 예약하지 않는다. FAIL 결과의 기존 ABORT 동작은 변경하지 않는다.

회귀 사례는 비활성 상태 3종, 유효기간 직전/정확한 경계/직후, 검증 시각 누락, 최종 PAUSE, 재검증 후 다음 시도 복구, Worker 호출 도중 disable/만료/연결 ID 변경을 포함한다. mock 저장소/Worker와 실제 AnalysisJob 상태 전이를 사용한다.

추가로 사용자 설정 유효기간 적용, Worker FAIL 결과의 ABORT 유지, PASS 처리 시 연결 ID 누락도 검사한다.

새 회귀 사례 15개를 포함해 로컬 서버 전체 `mvnw --batch-mode verify`에서 125개 테스트가 실패·오류·건너뜀 없이 통과했다.

재조회와 다른 운영 트랜잭션을 전역으로 직렬화하거나 이미 예약된 PROMOTE 명령을 취소하는 기능은 아니다. Worker 호출 직전에 확인한 후 발생하는 비활성화는 진행 중 네트워크 요청 자체를 취소하지 않는다. 동일 연결 ID의 모든 설정 변경에 대한 별도 세대 snapshot을 제공하지 않는다. 실제 운영 연결을 비활성화하지 않았으며 공개 배포 이미지도 변경하지 않았다.
