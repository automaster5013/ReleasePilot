# 역할별 UI 인수 검증

2026-09-15

최종 로컬 결과: 웹 단위 34개, lint, typecheck, 새 production build 및 전체 fixture E2E 20개가 통과했다.

`apps/web-console/e2e/roles.spec.ts`에 DEVELOPER/APPROVER/OPERATOR 단일 역할 세션을 복원하는 격리 fixture E2E 11개를 추가했다. 기존 VIEWER/샘플/반응형 9개와 함께 Chromium에서 20개를 실행한다. 조직 SSO를 연결하지 않고 GET session fixture로 역할을 제공한다.

| 역할 | 검증 |
|---|---|
| DEVELOPER | Project→Service→Environment 선택, readiness 조회, 필수 입력, 요청 POST의 대상·버전·기본 정책 null, 생성 직후 상세 자동 로드, 승인 버튼 없음·운영 버튼 비활성화 |
| DEVELOPER | 만료된 readiness에서 요청 버튼 비활성화·mutation 없음 |
| APPROVER | 승인/거부 사유 trim, 정확한 대상 endpoint POST, 결정 성공 안내·감사 재조회·결정 버튼 제거 |
| APPROVER | backend fixture의 SELF_APPROVAL_NOT_ALLOWED 거부 시 오류 안내·승인 controls 유지 |
| APPROVER | 만료 readiness에서 Approve 차단·Reject 유지·mutation 없음 |
| OPERATOR | Promote/Pause/Resume/Abort 각각 사유 trim·정확한 대상 endpoint POST·접수 안내 |
| OPERATOR | 사유 prompt 취소 시 mutation 없음 |

fixture는 모든 외부 origin 요청을 차단하고 예상하지 않은 API/mutation을 기록해 테스트를 실패시킨다. 역할별 허용 mutation 외에 요청을 보내거나 POST에 합성 CSRF·Idempotency-Key·JSON Content-Type이 없으면 403을 반환하고 실패 기록을 남긴다. mutation은 fixture에서만 처리되며 실제 backend·Kubernetes·Prometheus·외부 저장소를 호출하지 않는다. 합성 pipeline URL은 화면 링크 값이며 탐색하지 않는다.

초기 실행의 요청 select exact-label과 자기 승인 오류 alert 선택자가 timeout/strict-mode 오류를 일으켰다. select의 옵션 문구와 Next.js route announcer를 고려해 선택자를 고쳤으며 전체 20개를 재실행해 통과했다. 제품 코드는 이번 작업에서 변경하지 않는다.

```powershell
cd C:\ReleasePilot\apps\web-console
npm test
npm run lint
npm run typecheck
npm run build
npm run test:e2e
```

기존 CI web-console job이 같은 Playwright testDir를 실행하므로 새 역할별 테스트도 자동 실행한다. production standalone build에서 실행하는 fixture UI 검증이며 실제 SSO 인증·서버 권한 enforcement·실제 배포 완료·DB 감사 저장을 증명하지 않는다. 운영 조작의 접수 안내를 검증하며 Rollout 완료를 검증하는 것이 아니다.

## 서버 거부·사유 입력 경계 추가 검증 (2026-09-16)

최대 허용 사유 길이 후속 검증: APPROVER 승인과 OPERATOR Abort에서 한글 1000자 앞뒤에 공백을 붙여 입력한 뒤 trim된 1000자가 정확히 전송되고 성공 안내가 표시되는지 검증했다. 기존 1001자 거부 테스트와 허용 경계를 함께 확인한다. lint/typecheck 및 전체 fixture E2E 36개가 통과했다. 제품 코드 변경은 없고 실제 backend의 길이 제한 검증은 별도다.

후속 [역할별 모바일 검수](role-mobile-review.md)에서 320px·390px 역할별 6개 시나리오와 운영자 연결 검증 헤더 넘침 수정을 완료했다. 전체 fixture E2E는 34개다.

fixture E2E 8개를 추가했다. DEVELOPER 요청과 APPROVER 승인에서 서버의 ENVIRONMENT_VALIDATION_STALE 응답을 표시하고 성공 안내 없이 버튼을 다시 사용할 수 있는지 확인한다. OPERATOR Abort의 FORBIDDEN 응답에서도 접수 안내 없이 오류를 표시하고 busy 상태를 해제한다. APPROVER/OPERATOR의 공백 사유·1001자 사유 및 승인 prompt 취소는 mutation이 전송되지 않는지 확인한다. 모든 시나리오에서 예상하지 않은 요청이 없어야 한다.

웹 단위 34개, lint, typecheck, 새 production build 및 전체 fixture E2E 28개가 통과했다. 제품 코드 변경은 없으며 실제 backend 장애나 권한 enforcement 검증을 의미하지 않는다.

연결 등록/재검증, 세션 원격 종료, API 지연·중복 클릭과 대상 전환, 전체 키보드·스크린리더 접근성 및 역할별 모바일 검수는 별도 과제다. 기존 함수 단위 보호 검증과 이번 화면 흐름 검증을 구분한다. 새 운영 배포·운영 데이터 변경은 없고 전체 운영 E2E와 SSO/GitHub Checks 활성화 보류를 유지한다.
