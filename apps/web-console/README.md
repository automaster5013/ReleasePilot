# Web Console

Next.js 기반 ReleasePilot 웹 콘솔입니다.

조직 사용자는 권한 범위의 Project → Service → 검증된 Environment를 선택해 릴리스를 요청할 수 있습니다.
카탈로그 조회 실패나 선택 가능한 환경이 없는 경우에는 요청 전 화면에서 확인할 수 있습니다.
릴리스 상세를 열면 Approver가 대상, 요청자, artifact와 Pipeline, 고정된 정책 단계와 지표 임계값을
확인한 뒤 승인 또는 거부할 수 있습니다.
로그인 사용자는 권한 범위의 최근 릴리스를 버전·상태·요청일로 선택하고 필요할 때 목록을 새로고침할 수
있으며, 새 요청은 생성 직후 목록과 상세 화면에 연결됩니다.

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
