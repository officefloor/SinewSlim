# Releasing sinew-slim

Distribution is automated by `.github/workflows/release.yml`. Pushing a version tag builds,
tests and publishes everything; this note covers the tag scheme and the one-time setup.

## Version scheme

- The version lives in `gradle.properties` (`version=0.1.0`). The jar's
  `Implementation-Version`, and so `sinew-slim --version`, come from it.
- Full version tags are `vMAJOR.MINOR.PATCH` (`v0.1.0`). These are the tags you push; the
  release workflow fails fast if the tag is not `v` + the version in `gradle.properties`.
- The workflow moves a floating major tag (`v0`) to the newest release on that major, so the
  Action can be pinned as `officefloor/SinewSlim@v0`.
- It also keeps a rolling GitHub Release named after the major tag (`v0`) holding the newest
  jar. The Action downloads `releases/download/<its own ref>/sinew-slim.jar`, so `@v0.1.0` gets
  that release's jar and `@v0` gets the rolling one. Any other ref (`@main`, a branch) builds
  the jar from source instead.

## Cutting a release

```bash
# 1. Set version= in gradle.properties, and add a <li><b>0.1.0</b>: …</li> entry to the
#    change-notes in intellij/src/main/resources/META-INF/plugin.xml (the Marketplace shows it;
#    the release fails fast without it). Commit both.
# 2. Tag and push.
git tag v0.1.0
git push origin v0.1.0
```

That one push:

- builds and tests, and attaches `sinew-slim.jar` to a GitHub Release `v0.1.0` with generated
  notes;
- creates the rolling `v0` release if it is not there, and replaces its jar;
- builds and pushes the image `ghcr.io/officefloor/sinew-slim` tagged `0.1.0`, `0.1`, `0` and
  `latest`;
- moves the `v0` tag to this commit;
- runs the Plugin Verifier against every IDE of the plugin's range, and when all pass, signs
  the plugin and uploads it to the JetBrains Marketplace. JetBrains then reviews it, so the
  version appears there a day or two later. A verifier or Marketplace failure does not undo
  the GitHub release; fix it and re-run the failed jobs.

## One-time setup

### JetBrains Marketplace (the IntelliJ plugin)

The `marketplace` job reads four secrets: `PUBLISH_TOKEN` from the repository, and the signing
trio from the OfficeFloor organisation. Without `PUBLISH_TOKEN` it warns and skips; without the
signing secrets it publishes unsigned, with a warning.

1. **The first upload is by hand.** The Marketplace only accepts uploads from Gradle for a
   plugin that already exists there. Sign in at https://plugins.jetbrains.com with the
   JetBrains account that should own the listing, choose **Upload plugin**, and upload
   `sinew-slim-0.1.0.zip` from the v0.1.0 GitHub Release. Pick the Apache 2.0 license,
   category *Code tools*, and link the repository. Then, on the plugin's page, **Edit > Vendor**
   moves it to an *OfficeFloor* organisation (create one under your profile's
   Organizations), so others can be given access.
2. **`PUBLISH_TOKEN`:** a permanent token from https://plugins.jetbrains.com/author/me/tokens,
   set as a repository secret: `gh secret set PUBLISH_TOKEN -R officefloor/SinewSlim` (it asks
   for the value).
3. **Signing** uses the OfficeFloor identity shared by all OfficeFloor plugins: the
   organisation secrets `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD`, made,
   replaced and given to repositories by the scripts in
   [OfficeFloorMaintenance](https://github.com/officefloor/OfficeFloorMaintenance#jetbrains-plugin-signing),
   whose list includes this repository. Don't set them here as repository secrets: those
   would override the organisation's.

   To check signing locally with the identity's files: `CERTIFICATE_CHAIN="$(cat chain.crt)"
   PRIVATE_KEY="$(cat private_encrypted.pem)" PRIVATE_KEY_PASSWORD=… ./gradlew
   :intellij:signPlugin`, then `CERTIFICATE_CHAIN="$(cat chain.crt)" ./gradlew
   :intellij:verifyPluginSignature`.

To publish a version that was released before the secrets existed (v0.1.0 is uploaded by hand
in step 1, so this is only for a later one), re-run the release workflow's `marketplace` job.
Once the listing is live, add its link and the IDE's **Settings > Plugins > Marketplace**
install route to the README.

### GHCR (the Docker image)

No secret: the `docker` job pushes with the built-in `GITHUB_TOKEN`. **The package must stay
public**: the GitLab and Jenkins templates and the pre-commit hook pull
`ghcr.io/officefloor/sinew-slim:0` anonymously.

Nothing needs doing for that: the package is created by this public repository's workflow, so
it took the repository's public visibility on the first release (v0.1.0). Check it after a
release, or after any change to the organisation's package policy, with an anonymous pull:

```bash
docker logout ghcr.io
docker pull ghcr.io/officefloor/sinew-slim:0
```

If that fails with `unauthorized`, the package has become private: make it public at
https://github.com/orgs/officefloor/packages/container/sinew-slim/settings (Danger Zone >
Change visibility; public packages must be allowed at the organisation level first).

### The repository

The repository is public, which the Action and the release assets need to be reachable by
others; keep it so.

### GitHub Marketplace (optional)

Each release creates a GitHub Release, which is where a Marketplace listing lives. The first
listing is manual: edit the Release, tick "Publish this Action to the GitHub Marketplace", and
accept the agreement. Later releases can be published from the same screen.
