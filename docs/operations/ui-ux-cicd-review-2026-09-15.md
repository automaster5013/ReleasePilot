# UI/UX·CI/CD 1차 검수

기준 소스: `82514a4`, 공개 서비스: v0.55.0. 운영 배포·릴리스 mutation 없이 검수했다.

후속 [release CI gate 보강](release-ci-gate.md)에서 아래 P1의 workflow 연결을 수정하고 정적·negative 회귀 검증을 추가했다. 아래 표는 1차 검수 당시 상태이며 다른 발견 사항은 계속 남아 있다.

후속 [샘플 표시·모바일 상단 개선](ui-sample-mobile-refinement.md)에서 샘플 카드의 production 표시와 모바일 상단·조회 컨트롤을 수정했다. 로컬 production/fixture 검증이며 공개 서비스 배포는 아직 하지 않았다. 역할별 화면 검수·운영 E2E와 다른 CI/CD 과제는 남아 있다.

후속 [역할별 UI 인수 검증](role-ui-acceptance.md)에서 Developer 요청, Approver 승인·거부·만료·자기 승인 오류, Operator 네 가지 조작·사유 취소 fixture E2E 11개를 추가했다. 역할 세션은 fixture이며 실제 SSO/backend/운영 전체 E2E 및 전체 접근성 검수를 대신하지 않는다.

## 확인 결과

- 공개 UI: 데모 로그인, 새로고침 후 VIEWER 세션 복원, 변경 버튼 비활성화, 빈 최근 릴리스 목록을 브라우저에서 확인했다.
- 데스크톱 및 390px 모바일 표시를 확인했다. 모바일 검수 후 viewport를 원래대로 복원했다.
- 웹 단위 테스트 34개, lint, typecheck가 통과했다. 기존 production standalone 산출물에 대한 Chromium fixture E2E 4개도 통과했다. 이번 검수에서 새 build는 실행하지 않았다.
- 최신 소스 CI 실행 `34964709654`의 6개 job은 모두 success다. https://github.com/automaster5013/ReleasePilot/actions/runs/34964709654
- 마지막 이미지 release 실행 `34956878766`도 success다. https://github.com/automaster5013/ReleasePilot/actions/runs/34956878766
- CI는 secret scan, Java verify, Python Ruff/pytest, 웹 테스트/lint/typecheck/build/Chromium E2E, 정책 JSON 검증 및 YAML parse, MySQL 복원·통합·분리 JVM 복구 helper를 실행한다.
- 이미지 workflow는 AWS OIDC, 3개 이미지 빌드·ECR push, provenance/SBOM 요청, digest artifact, GitOps digest pin 갱신을 구현했다. Argo CD는 main overlay를 자동 동기화한다. Rollout 50% 단계는 명시적 수동 승격 대기다.

## 발견 사항

| 우선순위 | 발견 사항 | 근거·개선 방향 |
|---|---|---|
| P1 | release 소스의 CI 성공을 강제하는 gate가 workflow에 없다 | `release-images.yml`은 tag/manual trigger에서 바로 publish한다. `ci.yml`의 main/PR 실행과 연결되지 않았다. 이미지 publish/GitOps 갱신 전에 정확한 release SHA의 필수 검증 성공을 강제해야 한다. 현재 CI 성공 사실과 강제 gate 존재는 별개다. |
| P2 | 실제 릴리스가 없어도 샘플을 PRODUCTION RELEASE로 표시한다 | `page.tsx`의 고정 header와 초기 demo 데이터. 로그인 후 banner는 VIEW ONLY지만 실제 목록은 비고 카드에는 checkout v1.4.2/PASS가 남는다. 샘플 카드·근거에 예시임을 명시하고 실제 빈 상태와 구분해야 한다. |
| P2 | 모바일 상단·조회 버튼의 배치가 좁고 일관되지 않다 | 공개 390px 화면에서 브랜드/세션 문구/기본 스타일 데모 버튼이 밀집하고 불러오기 텍스트가 줄바꿈됐다. nav 반응형 배치, 버튼 스타일·최소 폭·줄바꿈 규칙을 보강해야 한다. |
| P2 | 역할별 실제 UI 흐름과 접근성/반응형을 CI에서 충분히 검사하지 않는다 | fixture E2E는 Desktop Chrome VIEWER 4개다. DEVELOPER/APPROVER/OPERATOR의 요청·승인·거부·재검증·실패 복구, 모바일·키보드 focus·접근성 검사가 별도로 필요하다. 함수 단위 검증은 해당 화면 흐름을 대신하지 않는다. |
| P2 | 병행 release와 오래된 release의 GitOps 갱신에 대한 제어가 명시되지 않았다 | release workflow에 concurrency가 없고 update-gitops가 main을 새로 checkout/push한다. 경쟁 push는 실패하거나 실행 순서에 따라 과거 release가 다시 pin될 수 있다. 병행 실행·재실행·배포 순서 정책과 동일 digest no-op 처리 검증이 필요하다. |
| P2 | 이미지 workflow 성공이 운영 배포 건강 상태의 자동 검증까지 의미하지 않는다 | workflow의 마지막은 GitOps commit/push이며 Argo CD/Rollout healthy, 공개 smoke, 실패 시 복구를 workflow에서 확인하지 않는다. 수동 Canary 승격은 의도한 운영 정책이며 결함 자체가 아니다. 관측·승격·복구 기준을 자동/수동으로 명확히 구분해야 한다. |

OpenAPI/query YAML 단계는 parse 검사다. API 계약 전체 의미 검증, Terraform/Kustomize 검증, 이미지 취약점 차단은 현재 CI workflow에 명시돼 있지 않다. branch protection/ruleset의 강제 여부, AWS IAM 설정 및 현재 클러스터 상태는 이번 검수에서 확인하지 않았다. 파일 검토만으로 부재를 단정하지 않는다.

## 검수 한계·다음 우선순위

실제 운영 릴리스가 없어서 공개 화면의 상세/판정/감사 전체 E2E는 여전히 미검증이다. fixture 상세 흐름과 운영 실제 backend 흐름을 구분한다. SSO/GitHub Checks 활성화 보류를 유지하며 운영 데이터·기존 전달 상태·last_error·IAM·인프라를 변경하지 않았다.

우선 release SHA의 CI gate를 보강하고, 샘플/실제 빈 상태의 표현과 모바일 header를 수정한 다음 역할별 fixture UI 인수 테스트를 확대한다. 운영 전체 E2E와 배포 후 healthy/smoke·복구 훈련은 별도 운영 범위로 남긴다. 이 문서는 1차 검수 결과이며 전체 UI/UX 승인 또는 CI/CD 완전성 선언이 아니다.
