# SecretMaterial 문자열 출력 보호

2026-09-15

`SecretResolver.SecretMaterial`의 기본 record 문자열 표현은 bearer token을 포함할 수 있다. `toString()`을 재정의하여 값과 무관하게 `SecretMaterial[bearerToken=[REDACTED]]`만 반환한다. Optional과 컬렉션의 문자열 표현에도 동일한 보호가 적용된다.

실제 외부 요청에 필요한 `bearerToken()` 접근자는 변경하지 않는다. 접근자를 직접 로그에 출력하면 이 보호를 우회하므로 금지한다. 메모리 삭제나 전체 로그 필터링을 제공하는 변경은 아니다.

회귀 테스트 3개는 직접 출력, Optional/컬렉션 출력, null/빈 값 출력을 검증한다. 로컬 서버 전체 `mvnw --batch-mode verify`는 85개 테스트가 실패·오류·건너뜀 없이 통과했다.

## JSON 직렬화 보호 후속 작업

record의 `bearerToken` 구성 요소에 `@JsonIgnore`를 적용한다. 애플리케이션에서 사용하는 Jackson의 기본 직렬화는 `SecretMaterial`을 `{}`로 출력하며, Map과 List 안에서도 토큰을 포함하지 않는다. 직접/중첩 JSON 출력 회귀 테스트 2개를 추가했다. 실제 외부 요청을 만드는 접근자와 Analysis Worker로의 의도적인 자격 증명 전달은 변경하지 않는다.

후속 작업의 로컬 서버 전체 `verify`는 87개 테스트가 실패·오류·건너뜀 없이 통과했다.

이 보호는 Jackson annotation을 존중하는 직렬화에 한정된다. annotation을 끄거나 다른 직렬화 라이브러리를 사용하거나 토큰 문자열을 직접 다른 DTO에 넣는 경우에는 적용되지 않는다. 모든 비밀 값의 직렬화를 전역 차단한 것으로 해석하지 않는다.

초기 작업은 코드 수준 보강이었다. 이후 [v0.51.0 배포 검증](../operations/security-hardening-v0.51.0.md)에서 공개 이미지 반영까지 확인했다. 운영 비밀 조회나 자격 증명 교체는 수행하지 않았다. 비밀 조회는 기존 `env:` 참조를 유지한다. Secrets Manager/Vault, SSO 및 GitHub Checks 활성화는 별도 작업이다.
