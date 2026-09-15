"""Offline restore drill: synthetic data only, no ports or production credentials."""

import gzip
import hashlib
import json
import re
import subprocess
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LABEL = "releasepilot.restore-drill"
PASSWORD = "throwaway-drill-only"


def docker(*args, data=None, timeout=60):
    result = subprocess.run(
        ["docker", *args], input=data, capture_output=True, timeout=timeout, check=False
    )
    if result.returncode:
        raise RuntimeError(
            f"Docker {args[0]} failed: {result.stderr.decode(errors='replace')[:500]}"
        )
    return result.stdout


def mysql(container, sql):
    return docker(
        "exec",
        "-i",
        "-e",
        f"MYSQL_PWD={PASSWORD}",
        container,
        "mysql",
        "-uroot",
        "--default-character-set=utf8mb4",
        "-N",
        "-B",
        "releasepilot",
        data=sql.encode(),
    )


def digest(data):
    return hashlib.sha256(data).hexdigest()


def unpack_backup(archive, checksum):
    if digest(archive) != checksum:
        raise ValueError("Backup checksum mismatch")
    return gzip.decompress(archive)


def dump(container, schema_only=False):
    args = [
        "exec",
        "-e",
        f"MYSQL_PWD={PASSWORD}",
        container,
        "mysqldump",
        "-uroot",
        "--single-transaction",
        "--routines",
        "--events",
        "--triggers",
        "--hex-blob",
        "--default-character-set=utf8mb4",
        "--set-gtid-purged=OFF",
        "--no-tablespaces",
        "--skip-comments",
    ]
    if schema_only:
        args.append("--no-data")
    return docker(*args, "releasepilot")


def inventory(container):
    names = (
        mysql(
            container,
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='releasepilot' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;",
        )
        .decode()
        .splitlines()
    )
    counts = {}
    checksums = {}
    for name in names:
        if not re.fullmatch(r"[A-Za-z0-9_]+", name):
            raise ValueError("Unexpected table name")
        counts[name] = int(mysql(container, f"SELECT COUNT(*) FROM `{name}`;"))
        checksum = (
            mysql(container, f"CHECKSUM TABLE `{name}` EXTENDED;")
            .decode()
            .strip()
            .split("\t")[-1]
        )
        if checksum == "NULL":
            raise ValueError(f"Checksum unavailable: {name}")
        checksums[name] = checksum
    return counts, checksums


def main():
    run_id = uuid.uuid4().hex
    containers = []
    started = time.monotonic()
    # Require a pre-existing image; never pull, publish ports, or mount user volumes.
    image = json.loads(docker("image", "inspect", "mysql:8.4"))[0]["Id"]
    try:
        for role in ("source", "restore"):
            container = (
                docker(
                    "create",
                    "--name",
                    f"rp-drill-{run_id}-{role}",
                    "--label",
                    f"{LABEL}={run_id}",
                    "--network",
                    "none",
                    "-e",
                    f"MYSQL_ROOT_PASSWORD={PASSWORD}",
                    "-e",
                    "MYSQL_DATABASE=releasepilot",
                    image,
                )
                .decode()
                .strip()
            )
            containers.append(container)
            docker("start", container)
            deadline = time.monotonic() + 120
            while True:
                try:
                    mysql(container, "SELECT 1;")
                    break
                except RuntimeError:
                    if time.monotonic() >= deadline:
                        raise RuntimeError("MySQL startup timed out") from None
                    time.sleep(1)
        source, restore = containers
        migrations = sorted(
            (ROOT / "apps/control-plane/src/main/resources/db/migration").glob(
                "V*__*.sql"
            ),
            key=lambda path: int(path.name.split("__")[0][1:]),
        )
        if not migrations:
            raise ValueError("No migrations found")
        for migration in migrations:
            mysql(source, migration.read_text(encoding="utf-8"))
        mysql(
            source,
            """
INSERT INTO user_accounts VALUES (UNHEX(REPEAT('11',16)),'drill-viewer','not-a-real-password-hash','복구 훈련 ✅',true,'2026-09-15 00:00:00');
INSERT INTO user_roles VALUES (UNHEX(REPEAT('11',16)),'VIEWER');
INSERT INTO projects VALUES (UNHEX(REPEAT('22',16)),'restore-drill','격리된 테스트','synthetic only','ACTIVE','2026-09-15 00:00:00');
INSERT INTO audit_events (id,aggregate_type,aggregate_id,event_type,actor_type,occurred_at,correlation_id,payload_json) VALUES (UNHEX(REPEAT('33',16)),'PROJECT',UNHEX(REPEAT('22',16)),'DRILL_SAMPLE','SYSTEM','2026-09-15 00:00:00',UNHEX(REPEAT('44',16)),JSON_OBJECT('message','가짜 감사 데이터 ✅','sample',true));
""",
        )
        expected = inventory(source)
        schema = dump(source, schema_only=True)
        archive = gzip.compress(dump(source), mtime=0)
        checksum = digest(archive)
        corrupted = bytes([archive[0] ^ 1]) + archive[1:]
        try:
            unpack_backup(corrupted, checksum)
        except ValueError:
            pass
        else:
            raise AssertionError("Corrupted backup was accepted")
        sql = unpack_backup(archive, checksum)
        docker(
            "exec",
            "-i",
            "-e",
            f"MYSQL_PWD={PASSWORD}",
            restore,
            "mysql",
            "-uroot",
            "--default-character-set=utf8mb4",
            "releasepilot",
            data=sql,
        )
        if inventory(restore) != expected:
            raise AssertionError("Restored table counts/checksums differ")
        if dump(restore, schema_only=True) != schema:
            raise AssertionError("Restored schema differs")
        report = {
            "result": "PASS",
            "image": image,
            "migrations": len(migrations),
            "tables": len(expected[0]),
            "rows": sum(expected[0].values()),
            "archive_sha256": checksum,
            "compressed_bytes": len(archive),
            "corruption_rejected": True,
            "elapsed_seconds": round(time.monotonic() - started, 2),
        }
    finally:
        # Delete only IDs created above, after verifying their per-run ownership label.
        for container in containers:
            metadata = json.loads(docker("inspect", container))[0]
            if (
                metadata["Id"] != container
                or metadata["Config"]["Labels"].get(LABEL) != run_id
            ):
                raise RuntimeError("Refusing cleanup of an unowned container")
            docker("rm", "-f", "-v", container)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
