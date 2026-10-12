from pathlib import Path
import subprocess
import tempfile
import unittest

from release_metadata import metadata, validate_release


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "tag.gpgsign", "false")
        self.sha = self.commit("pluginVersion = 2.1.1\n")
        self.git("update-ref", "refs/remotes/origin/main", self.sha)

    def git(self, *args):
        return subprocess.check_output(
            ["git", "-C", str(self.repo), *args], text=True, stderr=subprocess.PIPE
        ).strip()

    def commit(self, properties):
        (self.repo / "gradle.properties").write_text(properties, encoding="utf-8")
        self.git("add", "gradle.properties")
        self.git("commit", "-qm", "Fixture")
        return self.git("rev-parse", "HEAD")

    def test_draft_resolves_main_to_sha(self):
        self.assertEqual(
            {"sha": self.sha, "version": "2.1.1", "tag": "v2.1.1"},
            metadata(self.repo, "main"),
        )

    def test_main_ancestor_is_allowed(self):
        newer = self.commit("pluginVersion=2.1.2\n")
        self.git("update-ref", "refs/remotes/origin/main", newer)
        self.assertEqual(self.sha, metadata(self.repo, self.sha)["sha"])

    def test_unmerged_commit_is_rejected(self):
        unmerged = self.commit("pluginVersion=2.1.2\n")
        with self.assertRaises(subprocess.CalledProcessError):
            metadata(self.repo, unmerged)

    def test_branch_names_and_shell_text_are_rejected(self):
        for ref in ["develop", "--help", "$(touch unexpected)", "main\nsha=other"]:
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                metadata(self.repo, ref)
        self.assertFalse((self.repo / "unexpected").exists())

    def test_existing_tag_on_different_commit_is_rejected(self):
        self.git("tag", "v2.1.2", self.sha)
        newer = self.commit("pluginVersion=2.1.2\n")
        self.git("update-ref", "refs/remotes/origin/main", newer)
        with self.assertRaisesRegex(ValueError, "another commit"):
            metadata(self.repo, "main")

    def test_release_accepts_annotated_tag(self):
        self.git("tag", "-a", "v2.1.1", "-m", "Release")
        result = validate_release(self.repo, "v2.1.1", self.sha, self.sha)
        self.assertEqual(self.sha, result["sha"])

    def test_wrong_tag_or_mutable_target_is_rejected(self):
        self.git("tag", "v2.1.1")
        for tag, target in [("v9.0.0", self.sha), ("v2.1.1", "main")]:
            with self.subTest(tag=tag, target=target), self.assertRaises(ValueError):
                validate_release(self.repo, tag, self.sha, target)

    def test_release_tag_is_required(self):
        with self.assertRaises(subprocess.CalledProcessError):
            validate_release(self.repo, "v2.1.1", self.sha, self.sha)

    def test_moved_tag_is_rejected(self):
        self.git("tag", "v2.1.1")
        newer = self.commit("pluginVersion=2.1.2\n")
        self.git("update-ref", "refs/remotes/origin/main", newer)
        self.git("tag", "-f", "v2.1.1", newer)
        with self.assertRaises(ValueError):
            validate_release(self.repo, "v2.1.1", self.sha, self.sha)

    def test_wrong_checkout_is_rejected(self):
        self.git("tag", "v2.1.1")
        newer = self.commit("pluginVersion=2.1.2\n")
        self.git("update-ref", "refs/remotes/origin/main", newer)
        with self.assertRaisesRegex(ValueError, "checkout"):
            validate_release(self.repo, "v2.1.1", self.sha, self.sha)

    def test_invalid_properties_are_rejected(self):
        for properties in ["# No version\n", "pluginVersion=foo\n", "pluginVersion=2.1.1\npluginVersion=2.1.2\n"]:
            with self.subTest(properties=properties):
                sha = self.commit(properties)
                self.git("update-ref", "refs/remotes/origin/main", sha)
                with self.assertRaises(ValueError):
                    metadata(self.repo, "main")

    def test_prerelease_version_is_supported(self):
        sha = self.commit("pluginVersion=2.2.0-beta.1\n")
        self.git("update-ref", "refs/remotes/origin/main", sha)
        self.assertEqual("v2.2.0-beta.1", metadata(self.repo, "main")["tag"])


if __name__ == "__main__":
    unittest.main()
