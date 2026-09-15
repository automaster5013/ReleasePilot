"""Prevent release publication from bypassing validation of the release commit."""

import copy
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {
    "secrets",
    "control-plane",
    "analysis-worker",
    "web-console",
    "contracts",
    "mysql-restore-drill",
}


def validate(release, ci):
    jobs = release["jobs"]
    gate = jobs.get("validate", {})
    if gate.get("uses") != "./.github/workflows/ci.yml":
        raise ValueError("Release must reuse CI from the caller commit")
    if gate.get("permissions") != {"contents": "read"}:
        raise ValueError("Validation must have read-only permissions")
    if "workflow_call" not in ci["on"]:
        raise ValueError("CI must support reusable calls")
    if not REQUIRED <= ci["jobs"].keys():
        raise ValueError("Required validation jobs are missing")
    for name, dependency in (("publish", "validate"), ("update-gitops", "publish")):
        needs = jobs[name].get("needs", [])
        if isinstance(needs, str):
            needs = [needs]
        if dependency not in needs:
            raise ValueError("Release dependency gate is missing")
    for name in ("validate", "publish", "update-gitops"):
        if "if" in jobs[name]:
            raise ValueError("Release must use default success dependency conditions")
    for job in ci["jobs"].values():
        if "if" in job or job.get("continue-on-error") == "true":
            raise ValueError("Validation jobs must not be skipped or tolerate failure")
        for step in job.get("steps", []):
            if step.get("continue-on-error") == "true":
                raise ValueError("Validation steps must not tolerate failure")
            if step.get("uses", "").startswith("actions/checkout@") and (
                "ref" in step.get("with", {})
                or "repository" in step.get("with", {})
            ):
                raise ValueError("CI must check out the caller commit")


class ReleaseGateTests(unittest.TestCase):
    def setUp(self):
        # BaseLoader preserves GitHub's 'on' key rather than YAML 1.1 booleans.
        self.release = yaml.load(
            (ROOT / ".github/workflows/release-images.yml").read_text(),
            Loader=yaml.BaseLoader,
        )
        self.ci = yaml.load(
            (ROOT / ".github/workflows/ci.yml").read_text(), Loader=yaml.BaseLoader
        )

    def test_release_gate_and_validation_commit_are_preserved(self):
        validate(self.release, self.ci)

    def test_missing_publish_gate_is_rejected(self):
        del self.release["jobs"]["publish"]["needs"]
        with self.assertRaises(ValueError):
            validate(self.release, self.ci)

    def test_failure_bypass_is_rejected(self):
        for name in ("validate", "publish", "update-gitops"):
            with self.subTest(job=name):
                changed = copy.deepcopy(self.release)
                changed["jobs"][name]["if"] = "always()"
                with self.assertRaises(ValueError):
                    validate(changed, self.ci)

    def test_different_ci_revision_is_rejected(self):
        self.release["jobs"]["validate"]["uses"] = (
            "automaster5013/ReleasePilot/.github/workflows/ci.yml@main"
        )
        with self.assertRaises(ValueError):
            validate(self.release, self.ci)

    def test_different_checkout_revision_is_rejected(self):
        self.ci["jobs"]["control-plane"]["steps"][0]["with"] = {"ref": "main"}
        with self.assertRaises(ValueError):
            validate(self.release, self.ci)

    def test_missing_required_job_is_rejected(self):
        for name in REQUIRED:
            with self.subTest(job=name):
                changed = copy.deepcopy(self.ci)
                del changed["jobs"][name]
                with self.assertRaises(ValueError):
                    validate(self.release, changed)

    def test_gitops_bypass_is_rejected(self):
        del self.release["jobs"]["update-gitops"]["needs"]
        with self.assertRaises(ValueError):
            validate(self.release, self.ci)

    def test_tolerated_validation_failure_is_rejected(self):
        self.ci["jobs"]["control-plane"]["steps"][-1]["continue-on-error"] = "true"
        with self.assertRaises(ValueError):
            validate(self.release, self.ci)


if __name__ == "__main__":
    unittest.main()
