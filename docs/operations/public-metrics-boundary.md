# 공개 메트릭 접근 경계 (2026-09-15)

AWS Ingress의 광범위한 `/actuator` Prefix 전달을 제거했다. Control Plane으로 전달하는 actuator 경로는 `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`의 Exact 세 개다. 그 외 경로는 기존 Web Console fallback을 사용하며 메트릭을 전달하는 rewrite는 없다. 내부 Control Plane의 메트릭 endpoint와 애플리케이션 이미지는 변경하지 않았다.

`python -m unittest discover -s scripts -p test_public_ingress.py`는 현재 경로 allowlist, 넓은 actuator 경로, health Prefix 및 rewrite 설정의 거부를 검증한다. 4개 테스트와 Kustomize 렌더링/Ruff 검사가 통과했다. CI contracts job에서도 실행한다.

배포 revision `e4becc465879a4f5ba645c2ebbf2e91235f69c5a`에서 Argo CD Synced/Healthy 및 다음 실제 응답을 확인했다. HTTPS 요청은 인증서 검증을 유지했고, 당시 DNS에서 확인한 IP를 curl --resolve로 지정했다.

| 공개 경로 | HTTP |
|---|---|
| /actuator/prometheus | 404 |
| /actuator/info | 404 |
| /actuator/health | 200 |
| /actuator/health/liveness | 200 |
| /actuator/health/readiness | 200 |
| /health | 200 |
| /control-api/session/providers | 200 |
| /control-api/../../actuator/prometheus (path-as-is) | 400 |
| /api/../actuator/prometheus (path-as-is) | 404 |
| /api/v1/%2e%2e/%2e%2e/actuator/prometheus (path-as-is) | 404 |

Web Console Pod에서 `http://control-plane:8080/actuator/prometheus`를 요청해 200과 jvm_memory 메트릭 존재를 확인했다. 메트릭 원문/값은 보고서에 저장하지 않았다. 이는 내부 endpoint 도달 검증이며 Prometheus 서버의 지속 scrape 성공 자체를 검증한 것은 아니다.

이번 조치는 공개 Ingress 경계 차단이다. 내부 endpoint의 인증, 다른 진입점이나 향후 ingress 변경까지 자동으로 보호하는 조치는 아니므로 별도 네트워크·인증 검토가 필요하다. 모든 가능한 인코딩/우회 입력에 대한 보증으로 위 표를 해석하지 않는다. SSO/GitHub Checks는 계속 보류한다.
