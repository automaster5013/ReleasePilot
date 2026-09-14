# ADR-006: AWS EKS Auto Mode와 ingress-nginx

- 상태: Accepted
- 날짜: 2026-09-14

## 결정

공개 MVP는 서울 리전의 EKS Auto Mode에서 실행한다. 외부 트래픽은 AWS Network Load Balancer가 붙은 ingress-nginx로 받고, Argo Rollouts의 NGINX traffic routing으로 Canary 가중치를 조절한다. `releasepilot.kr`은 Route 53 hosted zone에 위임하고 external-dns와 cert-manager가 DNS 레코드와 TLS 인증서를 관리한다.

## 이유

ReleasePilot의 핵심은 Canary 실행과 판정이며, Argo Rollouts가 직접 지원하는 NGINX canary annotation 경로가 가장 짧다. Auto Mode는 노드와 핵심 클러스터 구성요소 운영 부담을 낮춘다. EKS Pod Identity로 external-dns 권한을 서비스 계정 하나에 한정할 수 있다.

## 결과와 트레이드오프

EKS control plane, Auto Mode 노드, NAT Gateway와 NLB 비용이 발생한다. 데모 데이터베이스는 비용을 낮추기 위해 클러스터 내부의 단일 MySQL StatefulSet을 사용하므로 다중 AZ 복구를 보장하지 않는다. 프로덕션 전환 시 RDS, private endpoint, AWS WAF와 별도 비밀 관리자로 교체한다.
