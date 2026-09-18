# GitHub Checks 연동

ReleasePilot은 서비스의 `repositoryUrl`이 `https://github.com/{owner}/{repo}` 형식일 때 릴리스
수명 주기를 같은 커밋의 `ReleasePilot` Check Run으로 게시할 수 있다.

| ReleasePilot 이벤트 | Check status | Check conclusion |
|---|---|---|
| 릴리스 요청 | `queued` | 없음 |
| 승인 및 Rollout 시작 | `in_progress` | 없음 |
| Rollout 성공 | `completed` | `success` |
| 거부 | `completed` | `action_required` |
| 실패 | `completed` | `failure` |
| 중단 | `completed` | `cancelled` |

## 보안과 실패 경계

- GitHub claim이나 GitHub 사용자 권한으로 ReleasePilot 승인을 대신하지 않는다. 승인 주체와
  separation-of-duty 검사는 기존 내부 사용자·프로젝트 역할이 계속 담당한다.
- API token은 DB나 Git 저장소에 저장하지 않고 `env:` secret reference로만 해석한다.
- 전달 레코드는 릴리스 변경과 같은 DB 트랜잭션에서 생성·갱신한다. 별도 worker가 GitHub로
  전달하므로 GitHub 장애가 릴리스 요청이나 Rollout을 롤백하지 않는다.
- 전달은 지수 backoff로 재시도하며, 세대 번호를 사용해 전송 중 발생한 최신 상태 갱신을
  유실하지 않는다. 다중 Pod의 중복 claim은 DB 비관적 잠금으로 방지한다.
- 전송 오류에는 token을 포함하지 않으며 GitHub 응답 본문은 최대 500자로 제한한다.

## 활성화

Check Run 쓰기는 GitHub App 전용 권한이다. AWS demo overlay는 `releasepilot-github-checks` Secret의
`private-key.pem` 키를 `/connector-credentials/github-checks-private-key.pem`에 읽기 전용으로
투영한다. Control Plane은 App JWT로 1시간짜리 installation token을 발급하고 만료 5분 전에
자동 갱신한다. 장기 installation token이나 개인 access token은 저장하지 않는다.

운영 토큰은 Git에 저장하지 않는다. 배포 전에 대상 namespace에 Secret을 생성한다.

```bash
kubectl -n releasepilot create secret generic releasepilot-github-checks \
  --from-file=private-key.pem=/secure/path/releasepilot-checks.private-key.pem
```

AWS demo overlay가 적용하는 런타임 설정은 다음과 같다.

```properties
GITHUB_CHECKS_ENABLED=true
GITHUB_CHECKS_APP_ID=4993433
GITHUB_CHECKS_INSTALLATION_ID=162831833
GITHUB_CHECKS_PRIVATE_KEY_PATH=/connector-credentials/github-checks-private-key.pem
```

기본 애플리케이션 값은 `GITHUB_CHECKS_ENABLED=false`다. AWS demo overlay는 전용 Secret이 있어야
Pod가 시작되는 fail-closed 구성으로 이를 활성화한다. GitHub App은 대상 저장소에만 설치하고
`Checks: write` 외에는 GitHub가 강제하는 `Metadata: read`만 허용한다. private key 교체 시 Secret의
동일 키를 갱신하면 다음 token 발급부터 새 키를 사용한다.
