# 이미지 release의 CI gate

2026-09-15

이전 `release-images.yml`은 tag/manual 실행에서 CI 성공 확인 없이 이미지 publish를 시작할 수 있었다. 이제 `validate` job이 같은 저장소의 `./.github/workflows/ci.yml`을 호출하고 `publish`가 `needs: validate`로 대기한다. `update-gitops`는 기존 `needs: publish`를 유지한다. 검증 실패·취소·건너뜀 시 기본 success 의존 조건으로 publish와 GitOps 갱신을 진행하지 않는다.

CI의 PR/main trigger는 유지하고 `workflow_call`을 추가했다. release 소스에 대해 secret scan, Java verify, Python lint/test, 웹 test/lint/typecheck/build/fixture E2E, 정책·계약 검사, MySQL 복원·통합·분리 JVM 복구의 기존 6개 job을 다시 실행한다. 과거 다른 SHA의 성공 결과를 조회·재사용하지 않는다. 상대 경로 reusable workflow는 caller와 같은 commit을 사용하고 CI checkout에 별도 ref/repository override가 없어 release 소스를 검사한다. [GitHub reusable workflow 문서](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows), [needs 문서](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idneeds).

validate job의 token 권한은 `contents: read`이며 배포용 AWS OIDC·GitOps write 권한을 주지 않는다. 새로운 인증 정보·IAM·인프라 변경은 없다. publish 및 update-gitops의 기존 배포 권한은 유지한다.

`scripts/test_release_ci_gate.py`의 8개 테스트가 정상 연결, publish gate 누락, always() 등의 의존 실패 우회, 다른 CI revision 호출, 다른 checkout ref, 필수 job 누락, GitOps gate 누락 및 continue-on-error 우회를 검사한다. CI contracts job에서도 실행한다.

```powershell
python -m unittest discover -s scripts -p test_release_ci_gate.py
python -m unittest discover -s scripts -p test_public_ingress.py
apps/analysis-worker/.venv/Scripts/ruff.exe check scripts/test_release_ci_gate.py
```

로컬 결과: release gate 8개·Ingress 경계 4개 테스트 및 Ruff 통과. 공식 actionlint v1.7.12로 두 workflow를 포함한 전체 workflow 구문·reusable 연결 검증이 통과했다. shellcheck/pyflakes 연동은 비활성화하고 actionlint 자체 검증을 실행했다.

운영 배포 없이 검증하기 위해 tag 생성이나 release workflow dispatch는 실행하지 않는다. 따라서 release trigger에서 reusable 6개 job 실패가 실제 publish를 skip시키는 GitHub 실행은 이번 작업에서 미검증이다. 정적 dependency 검사·negative 회귀·GitHub 문서 semantics를 근거로 한다. main push CI 결과는 별도로 확인한다.

이 보강은 변경된 workflow를 포함한 release에 적용된다. 과거 commit의 tag는 해당 commit의 과거 workflow를 사용할 수 있으므로 소급 보호하지 않는다. workflow 파일 변경을 막는 branch protection/ruleset, 병행 release 순서, GitOps no-op, 취약점 gate와 배포 후 healthy/smoke/복구 자동화는 별도 과제다. 수동 Canary 승격 정책을 유지하며 전체 CI/CD 완전성을 선언하지 않는다. SSO/GitHub Checks 공급자 연결 보류와 운영 전체 E2E 미검증도 유지한다.
