# 릴리스 요청 timeout 복구

## 사용자 경험 계약

릴리스 생성 요청은 `fetchWithTimeout`을 통해 15초 안에 응답하지 않으면 `AbortController`로 중단한다. 화면에는 네트워크 상태를 확인한 뒤 다시 시도할 수 있다는 한국어 안내를 표시하고, `finally`에서 릴리스 요청 잠금을 항상 해제한다. 따라서 응답이 멈춰도 요청 버튼이 영구 비활성화되지 않으며 사용자는 같은 정책 입력으로 한 번의 깨끗한 재시도를 수행할 수 있다.

timeout이 아닌 서버·네트워크 오류는 원래 오류를 유지한다. timeout 값은 양수만 허용해 잘못된 호출이 무기한 요청으로 바뀌지 않도록 했다.

## 회귀 검증

- 단위 테스트 37개에서 abort signal 전달, timeout 오류, 일반 오류 보존과 잘못된 timeout 거부를 검증했다.
- 지연된 첫 릴리스 요청이 15초 뒤 종료되고 제어가 다시 활성화되며, 두 번째 요청만 성공하는 E2E를 추가했다. 10 worker 병렬 반복 10회와 Chromium 전체 184개가 통과했다.
- lint, typecheck, production build와 프로젝트 로그 관리 계약이 통과했다.
- CI `35219482647`(#279)의 6개 job이 6분 18초에 성공했다. Chromium 184개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 330개를 검증했다.

검증 명령의 실행 위치도 중앙 로그 helper의 `-WorkingDirectory`로 지정할 수 있게 했다. 경로는 저장소 내부의 기존 디렉터리로 제한하며, 해당 계약은 격리된 임시 저장소에서 검증한다.

## 운영 반영

Docker Hub CD `35219482721`(#79)은 변경된 web-console만 게시하고 9분 3초에 성공했다. GitOps commit `5716f86`은 web-console을 `sha256:e62c3e62cd5d84745b9d9563a58c1d8c3e8b84f2ba6a32362a29a8ca7fae9ccc`로 갱신했다.

Canary는 20% 설정 단계(복제본 반올림으로 실제 33%), 50%, 100% 순으로 확인하고 수동 승격했다. 각 단계에서 신규 Pod의 Ready 상태, 재시작 0, 정확한 digest를 확인했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이며 공개 root와 `/health`는 HTTPS 200을 반환했다. root에서 CSP와 HSTS, health에서 HSTS도 확인했다.
