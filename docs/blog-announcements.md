# Blog Announcement Handoff

This workflow only requests a review in `ByteAurora/ByteAurora.github.io`. It does
not create releases, publish plugins, merge PRs, or change Marketplace approval.
The existing `Release` workflow is unchanged.

Install the blog receiver on its default branch first. Configure
`CODEXP_BLOG_APP_CLIENT_ID` (variable) and `CODEXP_BLOG_APP_PRIVATE_KEY` (secret).
The App is installed only on the blog and the sender requests only Contents write
permission. Its short-lived token is revoked by the App-token action after the job.

The handoff is disabled until the maintainer explicitly sets
`CODEXP_BLOG_AUTOMATION_ENABLED=true`. Manual runs default to validation only and
must use `main`. Provide a successful `Release` run ID, e.g. `42146198805` for
2.3.0. A retry sends the same release identity and never reruns plugin publishing.

Only trusted `main` code runs with credentials, never code or artifacts from the
triggering run. A successful maintainer release must match its tag and fixed SHA.
The blog independently verifies the release, upload job, tag ancestry, and exact
public stable Marketplace version. Pending Marketplace approval produces no post.
The receiver has a separate reconciliation path for later approval.

Review the generated blog PR and its Jekyll/HTML checks before merging. Blog
workflow failures are separate from plugin release success. Initial rollout is
2.3.0 only; the sender does not scan or backfill older releases.

Validate locally with:

```sh
python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'
```
