# 프로젝트 로그 중앙 관리

## 저장 규칙

ReleasePilot의 로컬 실행·검증 로그는 저장소 루트의 `logs/`에 모은다. 기존 프로젝트 로그 86개(108,508,563바이트)는 파일명 충돌과 출처 손실을 막기 위해 저장소 기준 상대 경로를 보존해 이동했다. 의존성, 빌드 산출물, 패키지 캐시, 가상 환경과 Terraform 내부 로그는 해당 도구가 수명 주기를 관리하므로 중앙 이동 대상에서 제외한다.

새 명령은 `scripts/Invoke-WithProjectLog.ps1`로 실행하면 `logs/<category>/<timestamp>-<name>.log`에 출력이 저장되는 동시에 터미널에도 표시된다. 다른 위치에 남은 프로젝트 `.log` 파일은 `scripts/Move-ProjectLogs.ps1`로 일괄 정리할 수 있고 `-WhatIf`로 대상만 미리 확인할 수 있다. 실제 로그 내용은 기존 `*.log` ignore 규칙에 따라 Git에 포함하지 않는다.

## 회귀 검증

`scripts/Test-ProjectLogManagement.ps1`은 격리된 임시 저장소에서 다음 계약을 실제 파일 시스템으로 검증한다.

- 루트와 중첩 위치의 로그를 `logs/` 아래로 이동한다.
- 중첩 로그의 상대 경로를 보존한다.
- `node_modules`처럼 도구가 관리하는 경로는 변경하지 않는다.
- 실행 helper가 지정한 category에 로그를 만들고 명령 출력을 정확히 보존한다.
- 테스트가 소유한 임시 디렉터리만 정리한다.

계약은 CI `contracts` job에 연결했다. CI `35216408393`의 전체 6개 job과 Docker Hub CD `35216408788`이 성공했다.

## 운영 반영

워크플로 변경으로 세 이미지가 다시 게시되어 GitOps commit `e72cd4c`에 반영됐다. control-plane `sha256:64f0bdfd2c207f4ce8bd5a13d24e7ac864e1bb444ae607298f32011420707914`, analysis-worker `sha256:bbd55a59d50e9fd7db72ba3628e2efe0bbdfdcad4b877daf5a7db2afd61758d0`, web-console `sha256:4c3bff6940d81f22a0d4fe018dc5cba6bf905537fce50045e8e7f94b76de3c53`을 20%, 50%, 100% Canary 단계로 검증하고 stable로 승격했다.

최종 확인 시 세 Rollout은 각각 2/2 updated·ready이고 모든 Pod가 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
