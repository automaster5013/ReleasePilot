# v0.52.0 분석 안전성 배포 진행 기록

2026-09-15

## 완료한 단계

- 분석 요청 구성 실패, 분석/PASS Prometheus readiness, 예약 승격 실행 전 readiness 보강 포함
- [구현 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34948841523): success
- [릴리스 소스 CI](https://github.com/automaster5013/ReleasePilot/actions/runs/34949013104): success
- [세 이미지 빌드 및 GitOps 갱신](https://github.com/automaster5013/ReleasePilot/actions/runs/34949017548): success
- 릴리스 소스 `005bb14777073716274f2713c572f3ae7d8a1e90`, 태그 `v0.52.0`
- digest 반영 GitOps revision `e65265151cffbe9438e095381d6d21296836337d`

## 아직 완료하지 못한 단계

GitOps 반영 후 로컬 AWS 로그인 자격 증명이 ExpiredToken 상태가 되었으며 Kubernetes API는 Unauthorized를 반환했다. 새 Pod/Canary 상태를 확인하거나 수동 승격하지 않았다. GitOps 갱신 성공만으로 실제 배포 Healthy를 주장하지 않는다.

기존 releasepilot 프로필의 AWS 브라우저 재로그인을 시작했다. 인증 완료 후 caller identity, Argo 동기화 revision, 새 Pod 이미지/준비 상태/재시작/오류 로그를 확인한다. 세 Rollout이 50% 수동 대기 단계에 준비되면 승격하고 최종 2/2 Healthy 및 공개 HTTP 경계를 검증한다.

## 변경하지 않은 범위

IAM 권한이나 클러스터 인증 설정을 변경하지 않았으며 다른 서비스의 자격 증명을 우회 사용하지 않았다. SSO/GitHub Checks 보류는 유지했다. 빌드 전 공개 페이지의 HTTPS 200, HSTS/no-store/CSP, 9개 script nonce 일치 및 unsafe-eval 없음만 재확인했다. 이는 새 이미지 배포 후 검증을 대신하지 않는다.
