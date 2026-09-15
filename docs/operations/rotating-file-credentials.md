# 갱신 가능한 파일 자격 증명

연결 Secret resolver는 기존 `env:VARIABLE` 외에 `file:connector-token` 형식을 지원한다. `releasepilot.secrets.file-root`를 운영자가 지정한 읽기 전용 자격 증명 디렉터리로 설정해야 파일 참조가 활성화된다. 기본값은 비활성이다. Spring 환경 변수는 `RELEASEPILOT_SECRETS_FILE_ROOT`다.

파일 참조에는 디렉터리를 넣을 수 없고 단일 이름만 허용한다. 실제 경로가 설정된 root 밖으로 나가는 symlink는 거부한다. root와 파일은 신뢰할 수 있는 운영자만 변경할 수 있어야 한다. 일반 파일만 읽고 최대 16KiB, 공백이 없는 bearer token만 허용한다. 앞뒤 줄바꿈은 제거한다. 실패 시 빈 결과를 반환하며 자격 증명 내용을 로그로 출력하지 않는다.

매 resolve 호출마다 파일을 다시 열고 읽으므로 토큰 교체를 서버 재시작 없이 반영한다. 이미 실행 중인 HTTP 요청은 기존에 읽은 토큰을 사용한다. Kubernetes projected volume의 symlink 대상은 root 안에 있어야 한다. `subPath`로 단일 파일을 마운트하면 자동 갱신이 전달되지 않으므로 디렉터리를 마운트해야 한다.

이 변경은 파일 읽기 기능만 제공한다. TokenRequest 발급·갱신, projected volume 구성, EKS CA 신뢰와 실제 Java 연결 검증은 별도 구성해야 한다. 단기 토큰을 정적 파일에 넣는 것만으로 자동 갱신이 구현되지는 않는다. 현재 운영 배포에 파일 root나 volume을 추가하지 않았다.

회귀 테스트는 실행 중 파일 교체·삭제, 비활성 설정, 경로 참조 거부, 빈 토큰·내부 공백·크기 제한을 확인한다.

2026-09-16 로컬 `mvnw --batch-mode verify`: 새 테스트 4개 포함 전체 197개 통과(실패·오류·건너뜀 0), JAR 빌드 성공. 실제 projected-volume 회전과 운영 EKS 연결은 아직 검증하지 않았다.
