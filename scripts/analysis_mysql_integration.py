"""Run real analysis services against an owned, loopback-only throwaway MySQL."""

import json
import os
import subprocess
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LABEL = "releasepilot.analysis-mysql-test"
PASSWORD = "throwaway-analysis-test-only"


def docker(*args, data=None, timeout=60):
    result = subprocess.run(
        ["docker", *args], input=data, capture_output=True, timeout=timeout, check=False
    )
    if result.returncode:
        raise RuntimeError(f"Docker {args[0]} failed")
    return result.stdout


def main():
    owner = str(uuid.uuid4())
    container_id = None
    image = json.loads(docker("image", "inspect", "mysql:8.4"))[0]["Id"]
    try:
        container_id = (
            docker(
                "create",
                "--name",
                f"releasepilot-analysis-test-{owner}",
                "--label",
                f"{LABEL}={owner}",
                "--publish",
                "127.0.0.1::3306",
                "--env",
                f"MYSQL_ROOT_PASSWORD={PASSWORD}",
                "--env",
                "MYSQL_DATABASE=releasepilot",
                "--env",
                "MYSQL_USER=analysis_test",
                "--env",
                f"MYSQL_PASSWORD={PASSWORD}",
                image,
            )
            .decode()
            .strip()
        )
        docker("start", container_id)
        deadline = time.monotonic() + 120
        while True:
            try:
                docker(
                    "exec",
                    "-e",
                    f"MYSQL_PWD={PASSWORD}",
                    container_id,
                    "mysql",
                    "-uanalysis_test",
                    "releasepilot",
                    "-N",
                    "-e",
                    "SELECT 1",
                )
                break
            except RuntimeError:
                if time.monotonic() >= deadline:
                    raise RuntimeError("Temporary MySQL did not become ready") from None
                time.sleep(1)

        details = json.loads(docker("inspect", container_id))[0]
        bindings = details["NetworkSettings"]["Ports"]["3306/tcp"]
        if len(bindings) != 1 or bindings[0]["HostIp"] != "127.0.0.1":
            raise RuntimeError("Temporary MySQL must bind only to loopback")
        port = int(bindings[0]["HostPort"])
        if not 1 <= port <= 65535:
            raise RuntimeError("Invalid temporary port")
        test_env = os.environ.copy()
        test_env.update(
            {
                "ANALYSIS_TEST_JDBC_URL": f"jdbc:mysql://127.0.0.1:{port}/releasepilot?allowPublicKeyRetrieval=true&useSSL=false",
                "ANALYSIS_TEST_USERNAME": "analysis_test",
                "ANALYSIS_TEST_PASSWORD": PASSWORD,
                "ANALYSIS_TEST_FLYWAY": "true",
                "ANALYSIS_TEST_DDL": "validate",
            }
        )
        command = (
            ["cmd", "/d", "/c", "mvnw.cmd"] if os.name == "nt" else ["bash", "mvnw"]
        )
        command.extend(
            [
                "--batch-mode",
                "-Dtest=AnalysisFlowIntegrationTests,AuditStorageIntegrationTests",
                "test",
            ]
        )
        print(
            "Running 6 analysis flows and 5 audit storage checks: MySQL 8.4, Flyway enabled, Hibernate validate",
            flush=True,
        )
        subprocess.run(
            command,
            cwd=ROOT / "apps/control-plane",
            env=test_env,
            timeout=600,
            check=True,
        )
        for table, field, expected in (
            ("policy_versions", "definition", "OBJECT"),
            ("policy_snapshots", "definition", "OBJECT"),
            ("analysis_jobs", "evidence_json", "ARRAY"),
            ("outbox_commands", "payload_json", "OBJECT"),
            ("audit_events", "payload_json", "OBJECT"),
        ):
            sql = f"SELECT COUNT(*),COALESCE(SUM(JSON_TYPE({field}) <> '{expected}'),0) FROM {table};"
            row = (
                docker(
                    "exec",
                    "-e",
                    f"MYSQL_PWD={PASSWORD}",
                    container_id,
                    "mysql",
                    "-uanalysis_test",
                    "releasepilot",
                    "-N",
                    "-B",
                    "-e",
                    sql,
                )
                .decode()
                .strip()
                .split("\t")
            )
            if int(row[0]) < 1 or int(row[1]) != 0:
                raise RuntimeError(f"Unexpected stored JSON shape: {table}")
        migration_count = (
            docker(
                "exec",
                "-e",
                f"MYSQL_PWD={PASSWORD}",
                container_id,
                "mysql",
                "-uanalysis_test",
                "releasepilot",
                "-N",
                "-e",
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;",
            )
            .decode()
            .strip()
        )
        migration_dir = ROOT / "apps/control-plane/src/main/resources/db/migration"
        expected_migrations = len(list(migration_dir.glob("V*__*.sql")))
        if int(migration_count) != expected_migrations:
            raise RuntimeError("Unexpected successful migration count")
        print(
            f"Migration history: {migration_count} successful versions; 5 JSON columns have expected shapes",
            flush=True,
        )
    finally:
        if container_id:
            details = json.loads(docker("inspect", container_id))[0]
            if (
                details["Id"] != container_id
                or details["Config"]["Labels"].get(LABEL) != owner
            ):
                raise RuntimeError("Refusing cleanup: container ownership mismatch")
            docker("rm", "--force", "--volumes", container_id)
    print(
        "PASS: real MySQL analysis flows; owned container and anonymous volumes removed",
        flush=True,
    )


if __name__ == "__main__":
    main()
