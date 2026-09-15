# SecretMaterial 문자열 출력 보호

2026-09-15

`SecretResolver.SecretMaterial`의 기본 record 문자열 표현은 bearer token을 포함할 수 있다. `toString()`을 재정의하여 값과 무관하게 `SecretMaterial[bearerToken=[REDACTED]]`만 반환한다. Optional과 컬렉션의 문자열 표현에도 동일한 보호가 적용된다.

실제 외부 요청에 필요한 `bearerToken()` 접근자는 변경하지 않는다. 접근자를 직접 로그에 출력하거나 JSON으로 직렬화하면 이 보호를 우회하므로 금지한다. 메모리 삭제, 전체 로그 필터링, 자동 JSON 직렬화 차단을 제공하는 변경은 아니다.

회귀 테스트 3개는 직접 출력, Optional/컬렉션 출력, null/빈 값 출력을 검증한다. 로컬 서버 전체 `mvnw --batch-mode verify`는 85개 테스트가 실패·오류·건너뜀 없이 통과했다.

이번 작업은 코드 수준 보강이다. 공개 데모 배포 이미지는 변경하지 않았으며, 운영 비밀 조회나 자격 증명 교체도 수행하지 않았다. 비밀 조회는 기존 `env:` 참조를 유지한다. Secrets Manager/Vault, SSO 및 GitHub Checks 활성화는 별도 작업이다.
