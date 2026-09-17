# Project logs

ReleasePilot의 로컬 실행·검증 로그는 이 디렉터리에 모은다. 새 명령을 로그와 함께 실행할 때는 저장소 루트에서 다음 helper를 사용한다.

```powershell
.\scripts\Invoke-WithProjectLog.ps1 -Name web-tests -Category web -Executable npm -ArgumentList "test"
```

저장소의 다른 위치에 남은 프로젝트 `.log` 파일은 다음 명령으로 기존 상대 경로를 보존해 이 디렉터리 아래로 옮긴다.

```powershell
.\scripts\Move-ProjectLogs.ps1
```

의존성, 빌드 산출물, 패키지 캐시와 Terraform 내부 로그는 각 도구가 관리하므로 이동 대상에서 제외한다. 실제 로그 파일은 Git에 포함하지 않는다.
