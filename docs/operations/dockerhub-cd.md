# Docker Hub CI/CD

이미지 입력 경로의 main push 또는 수동 실행 → 같은 commit의 재사용 CI 6개 검증 → 세 서비스 이미지 병렬 게시 → 선택적 GitOps digest 갱신.

GitHub Actions repository secrets에 DOCKERHUB_USERNAME=automaster5013 및 쓰기 권한 PAT인 DOCKERHUB_TOKEN을 등록한다. 토큰은 채팅이나 Git 파일에 저장하지 않는다. Docker Hub에 releasepilot-control-plane, releasepilot-analysis-worker, releasepilot-web-console 저장소를 준비한다. 기존 AWS 노드가 인증 없이 pull하려면 public 저장소가 필요하며 private 사용 시 별도 imagePullSecret을 구성해야 한다.

태그는 소스 commit SHA이며 배포는 게시 결과의 sha256 digest로 고정한다. 세 게시 job 성공 전에는 배포 파일을 갱신하지 않는다. `dockerhub-cd-main` concurrency group은 실행을 직렬화하고 실행 중 작업을 취소하지 않는다. 오래된 소스의 배포는 main HEAD 검사로 건너뛰며 동일 digest는 commit을 만들지 않는다. 배포 파일만 바뀐 push는 새 Docker Hub CD를 실행하지 않는다. 기존 ECR release workflow는 유지된다. 병행 ECR release와 Docker Hub 배포는 운영 시 조정해야 한다.

각 게시 job은 Docker Hub에 push된 정확한 digest를 Trivy Action v0.36.0의 검증된 commit SHA로 스캔한다. OS와 애플리케이션 라이브러리에서 수정 가능한 CRITICAL 취약점이 하나라도 발견되면 exit code 1로 digest artifact 생성을 막고, 따라서 GitOps 갱신과 배포 검증도 실행되지 않는다. 아직 수정본이 없는 취약점은 로그에는 포함될 수 있지만 초기 차단선에서는 제외한다. provenance와 SBOM 생성은 계속 유지한다.

실제 fail-closed 검증에서 source commit `5e063d1`의 Docker Hub CD run 35054627588은 control-plane의 Tomcat 11.0.24와 analysis-worker Debian 이미지의 perl-base에서 수정 가능한 CRITICAL 취약점을 발견해 GitOps 전에 실패했다. Tomcat 11.0.25 고정과 Debian 보안 업데이트를 적용한 source commit `3201c8e`는 CI run 35055102360과 Docker Hub CD run 35055102462를 통과했다. GitOps commit `43c49e5`가 두 새 digest를 반영했고, Canary 경계 자동 검증 성공 뒤 수동 전체 승격하여 두 Rollout과 기존 web-console이 모두 Healthy 2/2, Argo CD Synced/Healthy, 공개 readiness HTTP 200임을 확인했다.

취약점 검사를 통과한 각 digest에는 `actions/attest` v4의 검증된 commit SHA로 GitHub Actions OIDC 기반 build provenance를 생성해 Docker Hub OCI registry에 게시한다. 이어서 `gh attestation verify`가 `automaster5013/ReleasePilot` 저장소를 발급 주체로 같은 digest를 검증해야 digest artifact를 생성한다. publish job에는 contents read, id-token write, attestations write, packages write만 부여한다. source commit `5490a53`의 CI run 35056609472와 Docker Hub CD run 35056609769에서 세 이미지의 attest/verify가 모두 성공했고, GitOps commit `6287d64` 반영과 Canary 경계 확인 뒤 수동 승격하여 세 Rollout Healthy 2/2, Argo CD Synced/Healthy, 공개 readiness HTTP 200을 확인했다. 현재 강제 지점은 CD의 GitOps 이전이며, 클러스터에서 임의의 수동 이미지 변경까지 거부하는 admission policy는 아직 설치하지 않았다.

자동 게시 push 경로는 `apps/**`, 재사용 CI/CD workflow 두 개, GitOps 이미지 갱신 스크립트로 제한한다. 문서, 배포 digest commit, 계약 테스트만 바뀐 push는 일반 CI로 검증하되 동일 애플리케이션 이미지를 다시 빌드·게시하지 않는다. `workflow_dispatch` 수동 전체 게이트·게시 기능은 유지한다.

push 실행은 두 commit 사이의 변경 경로로 게시 matrix를 생성한다. `apps/control-plane`, `apps/analysis-worker`, `apps/web-console` 중 바뀐 구성요소만 게시하며 CI/CD workflow 또는 이미지 선택·GitOps 갱신 스크립트 변경은 안전한 전체 게시를 선택한다. 수동 실행과 새 브랜치의 영(0) before SHA도 전체 게시한다. 부분 게시 artifact에는 변경된 digest만 포함되고 갱신 스크립트는 해당 Kustomize image entry만 교체해 나머지 digest를 보존한다.

경로 제한을 도입한 source commit `02e8d48`은 CI run 35051379879와 Docker Hub CD run 35051380078을 통과했고 GitOps commit `9261450`으로 세 운영 Rollout이 Healthy 2/2가 됐다. 이 문단만 추가하는 후속 docs-only commit에서는 일반 CI만 생성되고 Docker Hub CD 실행이 생성되지 않는지 확인한다.

구성요소 선택 게시 source commit `8bd7510`은 CI run 35052174905와 전체 안전 배포 run 35052175125를 통과했다. 이어서 web-console 입력만 바꾼 commit `bce60e1`에서 CI run 35052614889와 Docker Hub CD run 35052615152가 성공했고, 게시 job은 `publish (web-console, apps/web-console)` 하나만 생성됐다. GitOps commit `6f9dd02`의 diff는 web-console digest 한 줄뿐이며 control-plane/analysis-worker digest는 유지됐다. web-console Canary 승격 후 Argo CD Synced/Healthy, 세 Rollout Healthy 2/2, 공개 화면 HTTP 200을 확인했다.

사용자가 기존 AWS/Argo CD 배포를 지정했으므로 aws-demo overlay 자동 갱신을 기본 활성화한다. repository variable DOCKERHUB_AUTO_DEPLOY=false로 중단할 수 있다. Argo CD의 자동 sync 및 기존 수동 Canary 승격 정책은 별도이며 overlay 갱신만으로 Healthy/승격 완료를 보장하지 않는다. 별도 서버 배포는 주소·인증·배포 방식 확인 후 연결해야 한다.

GitOps 갱신 뒤 `verify-deployment`는 AWS OIDC release 역할로 EKS에 접속한다. IAM 신뢰 조건은 같은 저장소의 `refs/tags/v*`와 `refs/heads/main`으로 제한한다. 역할은 `eks:DescribeCluster`와 전용 `releasepilot-deploy-verifier` 그룹만 가진다. GitOps RBAC는 이 그룹에 세 운영 Rollout 이름과 `releasepilot-demo` Application 이름에 대한 `get`만 부여하며 list/watch 및 Kubernetes mutation 권한은 없다. 선택 게시된 Rollout의 spec image가 게시 digest와 일치하고 phase가 Paused 또는 Healthy이며 updated replica가 하나 이상인지, Argo CD가 Synced인지, 공개 readiness가 HTTP 성공인지 최대 15분 동안 확인한다. 성공은 수동 Canary 승인 경계까지 안전하게 도달했다는 뜻이며 50% 이후 승격 완료를 뜻하지 않는다.

첫 run 35053467003은 기존 IAM 신뢰 정책이 태그만 허용해 OIDC assume 단계에서 fail-closed 됐다. main subject를 추가하고 CRD 전용 읽기 RBAC를 적용한 failed-job 재실행은 `verify-deployment`를 포함해 성공했다. 게시와 GitOps가 이미 성공한 상태에서 검증 실패가 전체 CD 실패로 표시됐고, 권한 수정 후 동일 artifact/digest를 재검증했다.

2026-09-16: GitHub Secrets 두 개 등록 후 run 34998468081의 재실행에서 CI 6개 및 세 이미지 게시가 성공했다. Docker Hub API로 소스 SHA 9800909c5d868b497eca75628b74cf75432f3688의 세 태그 active/digest를 확인했다. 자동배포 변수는 미활성 상태로 update-gitops는 skipped다. 배포 대상 확인이 필요하며 운영 배포 완료는 클러스터 상태로 별도 확인해야 한다.

2026-09-16 후속 운영 검증: run 35049863342의 CI 6개와 run 35049863500의 Docker Hub 세 이미지 게시·GitOps 갱신이 성공했다. GitOps commit 4f62387이 Argo CD에서 Synced/Healthy가 됐고 control-plane, analysis-worker, web-console은 각각 updated/available 2/2와 Healthy를 확인했다. 기존 50% Canary pause는 준비 상태 확인 후 명시적으로 승격했다. 따라서 자동화 범위는 CI, 이미지 게시, digest GitOps 갱신, Argo CD 자동 sync까지이며 운영 Canary 승격은 의도한 수동 정책이다.

scripts/test_dockerhub_cd.py의 회귀 4개가 CI contracts에서 게시 gate·서비스/SHA/digest·배포 활성화/직렬 concurrency/HEAD/no-op·배포 commit 반복 방지를 검사한다. 정적 workflow 계약과 위 실제 운영 검증을 구분한다.
