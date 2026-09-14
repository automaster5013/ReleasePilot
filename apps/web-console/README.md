# Web Console

Next.js 기반 ReleasePilot 웹 콘솔입니다.

조직 사용자는 권한 범위의 Project → Service → 검증된 Environment를 선택해 릴리스를 요청할 수 있습니다.
카탈로그 조회 실패나 선택 가능한 환경이 없는 경우에는 요청 전 화면에서 확인할 수 있습니다.
Environment를 선택하면 최신 Kubernetes·Prometheus 점검 결과, 검증 시각과 실패·경고 요약을 요청 전에 확인할 수 있습니다.
OPERATOR는 같은 화면에서 환경을 즉시 재검증할 수 있고, 재검증 중 중복 요청과 실패 상태의 릴리스 요청은 차단됩니다.
수동 재검증은 실행자를 포함한 감사 이벤트로 기록되며 OPERATOR 화면에 발생 시각과 chain sequence가 표시됩니다.
릴리스 상세를 열면 Approver가 대상, 요청자, artifact와 Pipeline, 고정된 정책 단계와 지표 임계값을
확인한 뒤 승인 또는 거부할 수 있습니다.
로그인 사용자는 권한 범위의 최근 릴리스를 버전·상태·요청일로 선택하고 필요할 때 목록을 새로고침할 수
있으며, 새 요청은 생성 직후 목록과 상세 화면에 연결됩니다.
목록 항목은 Service/Environment 대상을 함께 표시하고 서버가 권한 범위를 적용한 뒤 최신 항목 수를 제한합니다.
릴리스 상세의 감사 타임라인은 상태 변경 이벤트, actor, 발생 시각, correlation ID와 hash chain sequence를
시간순으로 보여주며 비운영자에게 원본 감사 payload를 노출하지 않습니다.
OPERATOR는 같은 화면에서 전체 감사 해시 체인의 무결성과 검증 이벤트 수를 확인하고 재검증할 수 있습니다.
검증 실패 시 최초 실패 이벤트가 경고로 표시되며, 일반 사용자와 공개 demo는 검증 API를 호출하지 않습니다.

## 실행

```powershell
npm install
npm run dev
```

`http://localhost:3000`에서 확인합니다. `/health`는 컨테이너 probe용 JSON 응답을 제공합니다.

## 검사

```powershell
npm run lint
npm run typecheck
npm run build
```
