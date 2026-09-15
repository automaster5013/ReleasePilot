"""Guard the AWS public routing boundary; internal metrics remain unchanged."""

import copy
import unittest
from pathlib import Path

import yaml

HEALTH = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}


def validate(document):
    if document["spec"].get("defaultBackend"):
        raise ValueError("Unexpected public default backend")
    rules = document["spec"]["rules"]
    if len(rules) != 1 or rules[0]["host"] != "releasepilot.kr":
        raise ValueError("Unexpected public hosts")
    paths = rules[0]["http"]["paths"]
    allowed = {
        "/api": "Prefix",
        "/oauth2": "Prefix",
        "/login/oauth2": "Prefix",
        **{path: "Exact" for path in HEALTH},
    }
    controls = {
        entry["path"]: entry["pathType"]
        for entry in paths
        if entry["backend"]["service"]["name"] == "control-plane"
    }
    if controls != allowed or len(paths) != len(allowed) + 1:
        raise ValueError("Public Control Plane routes exceed allowlist")
    fallback = [entry for entry in paths if entry["path"] == "/"]
    if (
        len(fallback) != 1
        or fallback[0]["pathType"] != "Prefix"
        or fallback[0]["backend"]["service"]["name"] != "web-console"
    ):
        raise ValueError("Public fallback must use Web Console")
    annotations = document["metadata"].get("annotations", {})
    if (
        annotations.get("nginx.ingress.kubernetes.io/use-regex") == "true"
        or "nginx.ingress.kubernetes.io/rewrite-target" in annotations
    ):
        raise ValueError("Regex/rewrite routing requires a fresh security review")


class PublicIngressTest(unittest.TestCase):
    def setUp(self):
        self.document = yaml.safe_load(
            (
                Path(__file__).resolve().parents[1]
                / "deploy/overlays/aws-demo/ingress.yaml"
            ).read_text(encoding="utf-8")
        )

    def test_current_routes(self):
        validate(self.document)

    def test_broad_actuator_route_is_rejected(self):
        document = copy.deepcopy(self.document)
        entry = next(
            entry
            for entry in document["spec"]["rules"][0]["http"]["paths"]
            if entry["path"] == "/actuator/health"
        )
        entry.update(path="/actuator", pathType="Prefix")
        with self.assertRaises(ValueError):
            validate(document)

    def test_health_prefix_is_rejected(self):
        document = copy.deepcopy(self.document)
        entry = next(
            entry
            for entry in document["spec"]["rules"][0]["http"]["paths"]
            if entry["path"] == "/actuator/health"
        )
        entry["pathType"] = "Prefix"
        with self.assertRaises(ValueError):
            validate(document)

    def test_rewrite_is_rejected(self):
        self.document["metadata"]["annotations"][
            "nginx.ingress.kubernetes.io/rewrite-target"
        ] = "/actuator/prometheus"
        with self.assertRaises(ValueError):
            validate(self.document)


if __name__ == "__main__":
    unittest.main()
