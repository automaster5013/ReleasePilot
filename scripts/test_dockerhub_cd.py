"""Guard Docker Hub publication and deployment boundaries."""

import unittest
from pathlib import Path
import subprocess
import tempfile

import yaml
from scripts.select_dockerhub_images import select

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
        self.assertEqual(set(self.jobs["publish"]["needs"]), {"validate", "changes"})
        self.assertEqual(self.jobs["update-gitops"]["needs"], "publish")
        self.assertEqual(self.jobs["verify-deployment"]["needs"], "update-gitops")
        for job in self.jobs.values():
            self.assertNotIn("continue-on-error", job)
            self.assertNotIn("always()", job.get("if", ""))

    def test_deployment_verification_is_read_only_and_waits_for_canary(self):
        verify = self.jobs["verify-deployment"]
        self.assertEqual(verify["permissions"], {"contents": "read", "id-token": "write"})
        command = verify["steps"][-1]["run"]
        for boundary in ("status.sync.status", "status.phase", "status.updatedReplicas", "actuator/health/readiness"):
            self.assertIn(boundary, command)
        for mutation in ("kubectl apply", "kubectl patch", "argo rollouts promote"):
            self.assertNotIn(mutation, command)

    def test_all_services_publish_source_sha_and_digest(self):
        publish = self.jobs["publish"]
        self.assertEqual(publish["strategy"]["matrix"], "${{ fromJSON(needs.changes.outputs.matrix) }}")
        selector = str(self.jobs["changes"])
        self.assertIn("select_dockerhub_images.py", selector)
        self.assertIn("workflow_dispatch", selector)
        self.assertIn("git", selector)
        self.assertIn("diff", selector)
        build = next(step for step in publish["steps"] if step.get("id") == "build")
        self.assertIn("${{ github.sha }}", build["with"]["tags"])
        self.assertEqual(build["with"]["push"], "true")
        self.assertTrue(any("steps.build.outputs.digest" in str(step) for step in publish["steps"]))

    def test_critical_vulnerability_scan_blocks_digest_artifact(self):
        steps = self.jobs["publish"]["steps"]
        scan_index = next(index for index, step in enumerate(steps) if step.get("name") == "Block fixable critical vulnerabilities")
        record_index = next(index for index, step in enumerate(steps) if step.get("name") == "Record pinned image")
        self.assertLess(scan_index, record_index)
        scan = steps[scan_index]
        self.assertEqual(scan["uses"], "aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25")
        self.assertIn("steps.build.outputs.digest", scan["with"]["image-ref"])
        self.assertEqual(scan["with"]["exit-code"], "1")
        self.assertEqual(scan["with"]["ignore-unfixed"], "true")
        self.assertEqual(scan["with"]["severity"], "CRITICAL")

    def test_verified_provenance_blocks_digest_artifact(self):
        publish = self.jobs["publish"]
        self.assertEqual(
            publish["permissions"],
            {
                "contents": "read",
                "id-token": "write",
                "attestations": "write",
                "packages": "write",
            },
        )
        steps = publish["steps"]
        scan_index = next(index for index, step in enumerate(steps) if step.get("name") == "Block fixable critical vulnerabilities")
        attest_index = next(index for index, step in enumerate(steps) if step.get("name") == "Attest published image provenance")
        verify_index = next(index for index, step in enumerate(steps) if step.get("name") == "Verify provenance before GitOps")
        record_index = next(index for index, step in enumerate(steps) if step.get("name") == "Record pinned image")
        self.assertLess(scan_index, attest_index)
        self.assertLess(attest_index, verify_index)
        self.assertLess(verify_index, record_index)
        attest = steps[attest_index]
        self.assertEqual(attest["uses"], "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6")
        self.assertEqual(attest["with"]["push-to-registry"], "true")
        self.assertIn("steps.build.outputs.digest", attest["with"]["subject-digest"])
        verify = steps[verify_index]
        self.assertIn("gh attestation verify", verify["run"])
        self.assertIn("$GITHUB_REPOSITORY", verify["run"])

    def test_cluster_admission_policy_is_repository_scoped(self):
        values = yaml.safe_load(
            (ROOT / "deploy/security/github-attestation-policy-values.yaml").read_text()
        )["policy"]
        self.assertTrue(values["enabled"])
        self.assertEqual(values["organization"], "automaster5013")
        self.assertEqual(values["repository"], "ReleasePilot")
        self.assertEqual(
            values["images"],
            ["index.docker.io/automaster5013/releasepilot-**"],
        )
        self.assertEqual(
            values["exemptImages"],
            ["index.docker.io/library/mysql**"],
        )
        namespace = yaml.safe_load(
            (ROOT / "deploy/base/namespace.yaml").read_text()
        )
        self.assertEqual(
            namespace["metadata"]["labels"]["policy.sigstore.dev/include"],
            "true",
        )

    def test_cluster_admission_webhook_is_fail_closed_and_redundant(self):
        values = yaml.safe_load(
            (ROOT / "deploy/security/policy-controller-values.yaml").read_text()
        )["webhook"]
        self.assertEqual(values["replicaCount"], 2)
        self.assertEqual(values["failurePolicy"], "Fail")
        self.assertTrue(values["podDisruptionBudget"]["enabled"])
        self.assertEqual(values["podDisruptionBudget"]["minAvailable"], 1)
        required = values["affinity"]["podAntiAffinity"][
            "requiredDuringSchedulingIgnoredDuringExecution"
        ]
        self.assertEqual(
            {term["topologyKey"] for term in required},
            {"kubernetes.io/hostname", "topology.kubernetes.io/zone"},
        )
        for term in required:
            self.assertEqual(
                term["labelSelector"]["matchLabels"],
                {"control-plane": "policy-controller-webhook"},
            )

    def test_deployment_supports_disable_switch_and_checks_current_head(self):
        deploy = self.jobs["update-gitops"]
        self.assertEqual(deploy["if"], "vars.DOCKERHUB_AUTO_DEPLOY != 'false'")
        command = deploy["steps"][-1]["run"]
        self.assertLess(command.index("git rev-parse HEAD"), command.index("python scripts/"))
        self.assertIn('!= "$RELEASE_SHA"', command)
        self.assertIn("git diff --cached --quiet", command)

    def test_deployment_commit_does_not_trigger_publication_loop(self):
        paths = set(self.workflow["on"]["push"]["paths"])
        self.assertEqual(
            paths,
            {
                "apps/**",
                ".github/workflows/ci.yml",
                ".github/workflows/dockerhub-cd.yml",
                "scripts/select_dockerhub_images.py",
                "scripts/update_releasepilot_images.py",
            },
        )
        for excluded in ("docs/**", "deploy/**", "scripts/test_dockerhub_cd.py"):
            self.assertNotIn(excluded, paths)
        self.assertEqual(self.workflow["concurrency"]["group"], "dockerhub-cd-main")
        self.assertEqual(self.workflow["concurrency"]["cancel-in-progress"], "false")

    def test_change_selector_limits_component_and_expands_global_inputs(self):
        self.assertEqual(
            select({"apps/analysis-worker/src/releasepilot_analysis_worker/main.py"}),
            {"include": [{"name": "analysis-worker", "context": "apps/analysis-worker"}]},
        )
        expected = {"control-plane", "analysis-worker", "web-console"}
        for paths, select_all in (
            ({".github/workflows/ci.yml"}, False),
            ({"scripts/update_releasepilot_images.py"}, False),
            (set(), True),
        ):
            self.assertEqual({item["name"] for item in select(paths, select_all)["include"]}, expected)
        with self.assertRaises(ValueError):
            select({"docs/operations/dockerhub-cd.md"})

    def test_partial_digest_update_preserves_other_images(self):
        overlay = """images:
  - name: control-plane
    newName: old/control
    digest: sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  - name: analysis-worker
    newName: old/worker
    digest: sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  - name: web-console
    newName: old/web
    digest: sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            digest_file = root / "digests.txt"
            overlay_file = root / "kustomization.yaml"
            digest_file.write_text("web-console=new/web@sha256:" + "d" * 64 + "\n")
            overlay_file.write_text(overlay)
            subprocess.run(
                ["python", str(ROOT / "scripts/update_releasepilot_images.py"), str(digest_file), str(overlay_file)],
                check=True,
            )
            updated = overlay_file.read_text()
        self.assertIn("newName: new/web", updated)
        self.assertIn("digest: sha256:" + "d" * 64, updated)
        self.assertIn("newName: old/control", updated)
        self.assertIn("newName: old/worker", updated)


if __name__ == "__main__":
    unittest.main()
