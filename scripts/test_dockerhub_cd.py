"""Guard Docker Hub publication and deployment boundaries."""

import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


class DockerHubCDTests(unittest.TestCase):
    def setUp(self):
        self.workflow = yaml.load(
            (ROOT / ".github/workflows/dockerhub-cd.yml").read_text(),
            Loader=yaml.BaseLoader,
        )
        self.jobs = self.workflow["jobs"]

    def test_ci_gate_cannot_be_bypassed(self):
        self.assertEqual(self.jobs["validate"]["uses"], "./.github/workflows/ci.yml")
        self.assertEqual(self.jobs["publish"]["needs"], "validate")
        self.assertEqual(self.jobs["update-gitops"]["needs"], "publish")
        for job in self.jobs.values():
            self.assertNotIn("continue-on-error", job)
            self.assertNotIn("always()", job.get("if", ""))

    def test_all_services_publish_source_sha_and_digest(self):
        publish = self.jobs["publish"]
        self.assertEqual(
            {entry["name"] for entry in publish["strategy"]["matrix"]["include"]},
            {"control-plane", "analysis-worker", "web-console"},
        )
        build = next(step for step in publish["steps"] if step.get("id") == "build")
        self.assertIn("${{ github.sha }}", build["with"]["tags"])
        self.assertEqual(build["with"]["push"], "true")
        self.assertTrue(any("steps.build.outputs.digest" in str(step) for step in publish["steps"]))

    def test_deployment_supports_disable_switch_and_checks_current_head(self):
        deploy = self.jobs["update-gitops"]
        self.assertEqual(deploy["if"], "vars.DOCKERHUB_AUTO_DEPLOY != 'false'")
        command = deploy["steps"][-1]["run"]
        self.assertLess(command.index("git rev-parse HEAD"), command.index("python scripts/"))
        self.assertIn('!= "$RELEASE_SHA"', command)
        self.assertIn("git diff --cached --quiet", command)

    def test_deployment_commit_does_not_trigger_publication_loop(self):
        self.assertIn("deploy/**", self.workflow["on"]["push"]["paths-ignore"])
        self.assertEqual(self.workflow["concurrency"]["group"], "dockerhub-cd-main")
        self.assertEqual(self.workflow["concurrency"]["cancel-in-progress"], "false")


if __name__ == "__main__":
    unittest.main()
