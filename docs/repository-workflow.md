# Repository workflow

Use one focused branch and pull request per actionable issue. Read the issue and AGENTS.md first, inspect current code, and avoid repeating fixes already made locally or in another PR. Link the issue in the PR and record exact test outcomes. Keep device verification explicit: implementation, automated checks and phone verification are different evidence.

Recommended board statuses: Backlog, Ready, In progress, Review, Done. Start with issues #1 and #2. Issue #7 is the backlog index, not an implementation task. Larger coaching issues should be broken into independently reviewable steps before implementation. Do not automatically close a broad issue when only one step lands.

## Android CI

`.github/workflows/android-ci.yml` runs on pull requests targeting main, pushes to main, and manual dispatch. It uses JDK 17, Android platform 36 and the committed Gradle wrapper, then runs `testDebugUnitTest` and `assembleDebug`. This does not run instrumentation, Samsung integration or phone tests, nor release builds/signing. A failing test step still allows the debug build to run; either failure fails the job.

Actions are pinned to reviewed commit SHAs. The job has read-only repository permissions, does not persist checkout credentials, and disables Gradle caches so proprietary dependencies are not retained there. Only JVM test reports are uploaded for seven days. No APK, AAR, raw health data or diagnostic dump is published.

### Samsung SDK provisioning

Despite the earlier "optional" setup heading, the current app module directly references SDK classes and requires the API AAR to compile. This workflow preserves that integration; it does not introduce a fake SDK or remove Samsung functionality.

The owner must supply an authorized private HTTPS download of the exact API AAR obtained under Samsung's SDK terms. This is not an automatic download from Samsung and does not bypass their terms or login. If no suitable private location exists, leave CI explicitly blocked until an SDK-free build variant or another authorized provisioning route is implemented. Do not publish the AAR in this public repository, a public release or an artifact.

1. On your machine, calculate `sha256sum app/libs/samsung-health-data-api.aar`.
2. In repository Settings → Secrets and variables → Actions, add a **secret** `SAMSUNG_HEALTH_SDK_URL` containing the private HTTPS file URL (for example, an appropriately scoped signed URL). Do not paste it into issues, PRs or chat.
3. Add an Actions **variable** `SAMSUNG_HEALTH_SDK_SHA256` containing only the 64-character hash.
4. Provisioning verifies SHA-256 and the AAR structure before writing the ignored local SDK path. The URL is available only to the provisioning step. Refresh expired URLs and review the checksum when deliberately updating the SDK.

GitHub secrets cannot hold a typical SDK binary (the per-secret limit is 48 KB), so the workflow uses a private download URL rather than base64 AAR content. Fork PRs and Dependabot PRs do not receive this secret and will fail provisioning explicitly; that is not a passing Android check. Never use `pull_request_target` to run an external PR's code with this secret. Validate those contributions locally or review and apply them to a trusted branch before CI. Repository collaborators able to execute trusted branch workflows must be trusted with the dependency access.

References: [GitHub secret limits](https://docs.github.com/en/actions/reference/security/secrets), [secrets in workflows and forks](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

## Initial validation and main protection

CI is not operationally verified merely because this file exists. First configure the authorized SDK download, fix issue #1, and inspect a real Actions run. The CI setup change does not fix the known missing import. Record failures honestly, including any dependency or tooling problems uncovered by the first run.

Once `Android unit tests and debug build` reliably passes, the owner can configure a ruleset for main requiring pull requests and that exact status check. Do not require an approval from another person for a solo-maintained repository unless a reviewer is available. This PR does not change repository rules, merge settings or credentials. Do not enable a required check before provisioning is ready, or normal merges will be blocked.

## License decision

A public repository is not itself an open-source license. The owner still needs to choose a suitable license; none is selected by this workflow setup.
