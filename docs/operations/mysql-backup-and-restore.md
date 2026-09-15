# MySQL 백업·복구 훈련

## 현재 범위

AWS 데모 DB는 단일 MySQL 8.4 StatefulSet/PVC다. PVC는 백업이나 다중 AZ 복구를 보장하지 않는다. 이번 자동화는 실제 운영 DB가 아닌 **가짜 데이터의 격리된 복원 훈련**이다. 운영 자격 증명, 볼륨, 포트, AWS 자원을 사용하지 않는다.

```sh
docker pull mysql:8.4
python scripts/mysql_restore_drill.py
```

스크립트는 사전 설치된 image ID를 고정해 임시 source/restore 컨테이너 두 개를 만든다. network는 none이고 host 포트와 사용자 볼륨을 연결하지 않는다. 저장소의 SQL migration 전체를 숫자 순서로 적용하고 Unicode·BINARY ID·JSON·외래 키를 포함한 합성 데이터를 넣는다. Flyway 자체의 실행/이력 검증은 하지 않는다.

`mysqldump --single-transaction --routines --events --triggers --hex-blob --set-gtid-purged=OFF --no-tablespaces` 결과를 gzip으로 압축하고 SHA-256을 확인한다. 손상된 압축 데이터가 복원 전에 거부되는지도 검사한다. 두 번째 빈 DB에 복원한 뒤 전체 테이블의 row count/CHECKSUM TABLE EXTENDED 및 schema-only dump가 원본과 같은지 비교한다.

훈련용 압축 데이터는 프로세스 메모리에만 존재하고 종료 시 보존하지 않는다. 출력에는 이미지 ID, migration/table/row 수, archive digest와 훈련 시간만 남긴다. 출력 시간은 운영 DB의 RTO를 의미하지 않는다. SHA-256은 신뢰할 수 있는 별도 manifest가 없는 경우 악의적인 변경을 방지하는 인증 수단이 아니다.

종료/오류 시 실행 중 생성한 정확한 컨테이너 ID와 per-run label을 확인해 해당 컨테이너와 anonymous volume만 제거한다. 삭제 대상은 합성 테스트 데이터이고 재실행으로 재생성할 수 있다. 호스트 강제 종료 등으로 cleanup이 실행되지 않았다면 `docker ps -a --filter label=releasepilot.restore-drill`로 정확한 대상을 확인하고 승인된 테스트 자원만 정리한다. 광범위한 docker prune은 사용하지 않는다.

CI의 mysql-restore-drill job도 같은 훈련을 실행한다. 실제 운영 schema/data의 복구 성공을 대신하지 않는다.

## 운영 백업 도입 전에 결정할 사항

- 허용 데이터 손실(RPO), 복구 제한 시간(RTO), 보존 기간, 예산 및 데이터 취급 권한
- 암호화된 외부 저장소/KMS, 신뢰할 수 있는 manifest, 최소 권한과 정기 백업 실패 경보
- 일관된 백업 중 DDL 제한, DB 용량/쓰기 부하, dump 계정 권한과 자격 증명 교체
- 별도 복원 DB에 대한 정기 훈련과 실제 릴리스/정책/감사 체인·archive/outbox 무결성 확인
- 복원한 환경에서 worker/reconciler/GitHub Checks/archive 전송이 운영 외부 시스템을 변경하지 못하도록 격리
- 세션/인증 데이터 취급, 쓰기 중지·원본 보존·승인된 cutover와 rollback 절차

RDS 전환, 운영 dump 생성/외부 업로드, 예약 백업·보존 정책과 point-in-time recovery는 아직 구현하거나 활성화하지 않았다. 비용·권한·복구 목표를 결정한 뒤 별도 작업으로 진행한다. 감사 S3 WORM 보존은 DB 백업을 대체하지 않는다.

## 최초 훈련 결과 (2026-09-15)

로컬 MySQL 8.4에서 두 차례 통과했다. 최종 실행은 migration 22개, 테이블 28개, 합성 데이터 5행을 복원하고 전체 row count/table checksum/schema 비교와 손상 archive 거부를 통과했다. 임시 컨테이너와 볼륨은 cleanup 완료 후 PASS를 출력했다. Ruff 검사도 통과했다.

SSO·GitHub Checks 공급자 연결은 사용자 요청으로 보류 중이며 이번 훈련에서도 활성화하지 않는다.
