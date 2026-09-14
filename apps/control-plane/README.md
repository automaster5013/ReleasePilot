# Control Plane

Spring Boot 기반 ReleasePilot 제어 영역입니다.

## 실행

Java 21 이상과 Maven 3.9 이상이 필요합니다.

```powershell
mvn spring-boot:run
```

기본적으로 `localhost:3306/releasepilot` MySQL에 연결합니다. 환경 변수 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`로 변경할 수 있습니다.

## 테스트

```powershell
mvn test
```
