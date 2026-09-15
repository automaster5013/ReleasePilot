# 격리 릴리스 검증용 checkout

표준 라이브러리 HTTP 앱이며 `/health`, `/checkout/{id}`, `/metrics`를 제공한다. Dockerfile은 코드를 이미지에 포함한다. `APP_VERSION` build argument로 v1/v2를 만들고 실제 릴리스에는 레지스트리 digest를 사용한다. 이 앱은 운영 주문 처리용이 아니다.

`CHECKOUT_FAIL=true` 및 `CHECKOUT_DELAY_SECONDS`는 격리 실패·지연 주입용 런타임 설정이다. 실제 요청의 상태·처리 시간을 histogram으로 기록한다. 기본 label은 service_namespace=releasepilot-e2e,service_name=sample-checkout,deployment_environment=staging,release_track=stable,http_route=/checkout/{id}다.

트랙별 수집 시 Prometheus static target의 release_track label과 honor_labels=false를 사용한다. stable/canary Service가 같은 Pod를 가리키는 초기 상태에는 canary target이 첫 버전의 메트릭을 반복 수집할 수 있다. 두 서비스가 서로 다른 revision을 가리키는 실제 Canary 구간에서 비교를 검증해야 한다. 초기 상태의 canary series 존재만으로 트랙 분리를 검증했다고 판단하지 않는다.

GitHub Actions `Sample checkout images`는 수동 실행하며 기존 CI 통과 및 각 버전의 실제 컨테이너 HTTP 검증 후 Docker Hub에 SHA-v1/SHA-v2 태그로 게시하고 digest artifact를 남긴다. 이 workflow는 Kubernetes 이미지를 변경하거나 승격하지 않는다.
