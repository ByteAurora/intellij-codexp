import copy
import unittest

from blog_handoff import REPO, identity


class BlogHandoffTest(unittest.TestCase):
    def setUp(self):
        self.run = dict(id=12, event="release", path=".github/workflows/release.yml",
                        head_repository={"full_name": REPO}, actor={"login": "ByteAurora"},
                        status="completed", conclusion="success", head_branch="v2.3.0", head_sha="a" * 40)
        self.release = dict(id=34, tag_name="v2.3.0", target_commitish="a" * 40,
                            draft=False, prerelease=False, published_at="2026-11-06T01:47:57Z", body="Notes")

    def test_exact_payload(self):
        self.assertEqual(identity(self.run, self.release),
                         dict(release_id=34, run_id=12, tag="v2.3.0", sha="a" * 40))

    def test_untrusted_or_failed_run(self):
        for field, value in [("event", "pull_request"), ("path", ".github/workflows/ci.yml"),
                             ("conclusion", "failure"), ("status", "in_progress"),
                             ("actor", {"login": "someone"}), ("head_repository", {"full_name": "fork/repo"}),
                             ("head_sha", "b" * 40), ("head_branch", "main")]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                run = copy.deepcopy(self.run)
                run[field] = value
                identity(run, self.release)

    def test_invalid_release(self):
        for field, value in [("draft", True), ("prerelease", True), ("body", " "),
                             ("published_at", None), ("target_commitish", "main"),
                             ("tag_name", "v2.3.0/../../bad"), ("id", True), ("id", 0),
                             ("body", None)]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                release = copy.deepcopy(self.release)
                release[field] = value
                identity(self.run, release)


if __name__ == "__main__":
    unittest.main()
