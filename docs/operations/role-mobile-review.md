# 역할별 모바일 UI 검수

2026-09-16

DEVELOPER/APPROVER/OPERATOR 역할을 각각 320px·390px, 높이 900px Chromium viewport에서 격리 API fixture로 검증했다. 요청 폼 입력·readiness 조회, 승인/거부 버튼, Promote/Pause/Resume/Abort 버튼이 화면 너비 안에 있고 활성화되는지 확인한다. 요청 생성/승인/Promote 성공 흐름을 역할마다 실행하고 mutation 1개 및 예상하지 않은 요청 없음도 확인한다. 전체 페이지 가로 넘침을 검사하고 역할·너비별 전체 화면 캡처를 생성한다.

초기 검수에서 OPERATOR 외부 연결 검증 제목과 세 버튼이 한 줄로 배치되어 두 모바일 너비 모두 가로 넘침이 발생했다. `session.module.css`의 600px 이하 구간에서 제목과 버튼 영역을 세로 배치하고 버튼 줄바꿈 및 최소 높이 44px를 적용했다. 기존 모바일 연결 목록 배치는 유지한다.

수정 후 웹 단위 34개, lint, typecheck, 새 production build, 전체 E2E 34개(신규 모바일 6개 포함)가 통과했다. OPERATOR 320px, APPROVER 320px, DEVELOPER 390px 전체 캡처를 시각 검수했다. 캡처는 Playwright의 무시된 `test-results/role-{ROLE}-{WIDTH}.png`에 생성된다.

실제 기기·브라우저별 동작, 전체 키보드/스크린리더 접근성, 연결 등록/검증 성공·실패 흐름 및 실제 SSO/backend/운영 E2E는 별도다. 브라우저 기본 사유 prompt의 시각적 배치를 검수한 것은 아니다. 운영 배포나 실제 연결 검증 호출은 진행하지 않았다.
