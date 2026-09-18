from __future__ import annotations

import pathlib
import sys
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "deploy" / "overlays" / "production"


def documents(name: str):
    with (OVERLAY / name).open(encoding="utf-8") as source:
        return [item for item in yaml.safe_load_all(source) if item]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate() -> None:
    ingress = documents("ingress.yaml")[0]
    annotations = ingress["metadata"]["annotations"]
    require(annotations.get("nginx.ingress.kubernetes.io/force-ssl-redirect") == "true", "TLS redirect must be forced")
    require(bool(ingress["spec"].get("tls")), "Ingress TLS must be configured")

    external = documents("external-secret.yaml")
    require(any(item["kind"] == "ExternalSecret" for item in external), "ExternalSecret is required")
    require(not any(item["kind"] == "Secret" and "data" in item for item in external), "Plain Kubernetes Secret data is forbidden")

    policies = documents("network-policy.yaml")
    default_deny = next(item for item in policies if item["metadata"]["name"] == "default-deny")
    require(set(default_deny["spec"]["policyTypes"]) == {"Ingress", "Egress"}, "Default deny must cover ingress and egress")

    monitoring = documents("observability.yaml")
    rules = next(item for item in monitoring if item["kind"] == "PrometheusRule")
    alerts = {rule["alert"] for group in rules["spec"]["groups"] for rule in group["rules"]}
    require({"ReleasePilotControlPlaneDown", "ReleasePilotHighServerErrorRate", "ReleasePilotCertificateExpiringSoon"} <= alerts,
            "Availability, error-rate and certificate alerts are required")

    profile = (ROOT / "apps" / "control-plane" / "src" / "main" / "resources" / "application-production.yml").read_text(encoding="utf-8")
    require("local-login-enabled: false" in profile, "Production local login must be disabled")
    require("enabled: false" in profile and "same-site: strict" in profile, "Production demo and cookie hardening are required")


if __name__ == "__main__":
    try:
        validate()
    except (AssertionError, KeyError, TypeError, yaml.YAMLError) as error:
        print(f"production readiness validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
    print("production readiness manifests validated")
