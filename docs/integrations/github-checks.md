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

GitHub App installation token 또는 Checks 쓰기 권한이 있는 fine-grained token을 Kubernetes
Secret 등에서 환경 변수 `GITHUB_CHECKS_TOKEN`으로 주입한다. GitHub App을 사용할 경우 token이
만료되기 전에 외부 secret controller가 값을 갱신해야 한다.

```properties
GITHUB_CHECKS_ENABLED=true
GITHUB_CHECKS_SECRET_REF=env:GITHUB_CHECKS_TOKEN
GITHUB_CHECKS_TOKEN=...
```

기본값은 `GITHUB_CHECKS_ENABLED=false`다. 활성화 전에 대상 GitHub App 또는 token에 해당
repository의 `Checks: write` 권한을 부여한다.
