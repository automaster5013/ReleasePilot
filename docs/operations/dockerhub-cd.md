# Docker Hub CI/CD

main push 또는 수동 실행 → 같은 commit의 재사용 CI 6개 검증 → 세 서비스 이미지 병렬 게시 → 선택적 GitOps digest 갱신.

GitHub Actions repository secrets에 DOCKERHUB_USERNAME=automaster5013 및 쓰기 권한 PAT인 DOCKERHUB_TOKEN을 등록한다. 토큰은 채팅이나 Git 파일에 저장하지 않는다. Docker Hub에 releasepilot-control-plane, releasepilot-analysis-worker, releasepilot-web-console 저장소를 준비한다. 기존 AWS 노드가 인증 없이 pull하려면 public 저장소가 필요하며 private 사용 시 별도 imagePullSecret을 구성해야 한다.

태그는 소스 commit SHA이며 배포는 게시 결과의 sha256 digest로 고정한다. 세 게시 job 성공 전에는 배포 파일을 갱신하지 않는다. 오래된 소스의 배포는 main HEAD 검사로 건너뛰고, 배포 파일만 바뀐 push는 새 Docker Hub CD를 실행하지 않는다. 기존 ECR release workflow는 유지된다. 병행 ECR release와 Docker Hub 배포는 운영 시 조정해야 한다.

기존 AWS Argo CD 배포를 사용할 경우 repository variable DOCKERHUB_AUTO_DEPLOY=true를 설정하면 aws-demo overlay를 자동 갱신한다. 기본은 이미지 게시까지다. Argo CD의 자동 sync 및 기존 수동 Canary 승격 정책은 별도이며 overlay 갱신만으로 Healthy/승격 완료를 보장하지 않는다. 별도 서버 배포는 주소·인증·배포 방식 확인 후 연결해야 한다.

현재 자격 증명 및 배포 대상 확인이 필요하다. 실제 Docker Hub 게시와 운영 배포 완료는 workflow 실행 결과와 클러스터 상태로 별도 확인해야 한다.
