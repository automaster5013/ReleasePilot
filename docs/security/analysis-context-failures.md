# 분석 요청 구성 실패의 안전한 종료

2026-09-15

분석 작업 claim 이후 요청 구성 중 정책 JSON 파싱이나 관련 데이터 조회가 실패하면 기존에는 Worker 예외 처리 전에 예외가 전파되어 작업이 PROCESSING에 남았다. 실행 대상인 Step/Execution/Release를 확인한 이후의 요청 구성 실패는 `ANALYSIS_CONTEXT_UNAVAILABLE`로 처리한다.

Worker를 호출하지 않고 `INCONCLUSIVE` 및 빈 근거 `[]`를 기존 결과 처리에 전달한다. 재시도 여유가 있으면 RETRY_WAIT과 기본 대기 300초를 설정하고, 최대 횟수에 도달하면 완료 후 PAUSE_ROLLOUT을 예약한다. 파싱 오류, 정책 원문, 자격 증명이나 조회 예외 메시지를 저장하지 않는다.

정책의 루트는 객체이고 `metrics`는 배열이어야 요청을 구성한다. 개별 metric 규칙의 전체 유효성 검사는 기존 정책/Worker 검증을 유지한다. 정상 정책의 추가 관측 지연과 SECRET_UNAVAILABLE 처리도 유지한다.

회귀 검증은 null/빈 정책, 잘못된 JSON, JSON null/배열 루트, 누락·문자열 metrics, 누락 snapshot, 최대 시도 후 PAUSE, 다음 시도의 정책 복구를 포함한다. mock 저장소/Worker와 실제 작업 상태 전이를 사용한다. 정책 복구 테스트는 요청 구성의 재조회 동작을 검증하며 운영 snapshot 수정 기능을 추가하지 않는다.

새 회귀 사례 10개를 포함해 로컬 서버 전체 `mvnw --batch-mode verify`에서 110개 테스트가 실패·오류·건너뜀 없이 통과했다.

실행 대상을 찾기 위한 Job/Step/Execution/Release 조회 실패와 DB 자체 장애는 이 변경의 범위 밖이며 기존 lease 복구의 대상이다. 대상이 불명확한 상태에서 임의의 Rollout 명령을 예약하지 않는다. 공개 배포 이미지는 변경하지 않았다.
