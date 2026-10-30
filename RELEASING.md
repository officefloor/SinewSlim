# Releasing sinew-slim

Distribution is automated by `.github/workflows/release.yml`. Pushing a version tag builds,
tests and publishes everything; this note covers the tag scheme and the one-time setup.

## Version scheme

- **The tag is the version.** Release tags are `vMAJOR.MINOR.PATCH` (`v0.2.0`); the release
  builds everything with `-Pversion=0.2.0`, so the jar's `Implementation-Version` (and
  `sinew-slim --version`), the plugin's version on the Marketplace, the plugin zip's name and
  the Docker image all carry it. Any other tag form fails the release before anything happens.
- `gradle.properties` holds only the development version (`0.0.0-SNAPSHOT`) for local builds;
  it is never edited for a release.
- The workflow moves a floating major tag (`v0`) to the newest release on that major, so the
  Action can be pinned as `officefloor/SinewSlim@v0`.
- It also keeps a rolling GitHub Release named after the major tag (`v0`) holding the newest
  jar. The Action downloads `releases/download/<its own ref>/sinew-slim.jar`, so `@v0.1.0` gets
  that release's jar and `@v0` gets the rolling one. Any other ref (`@main`, a branch) builds
  the jar from source instead.

## Cutting a release

```bash
# 1. Add a <li><b>0.2.0</b>: …</li> entry to the change-notes in
#    intellij/src/main/resources/META-INF/plugin.xml (the Marketplace shows it; the release
#    fails fast without it), and commit it.
# 2. Tag that commit and push the tag.
git tag v0.2.0
git push origin v0.2.0
```

That one push first checks, publishing nothing until all pass:

- the four publishing secrets are set (below), before anything else;
- the tag is `vMAJOR.MINOR.PATCH`, and `plugin.xml` has change notes for that version;
- the build and tests pass;
- the Plugin Verifier passes against every IDE of the plugin's range.

Then, in parallel, it:

- attaches `sinew-slim.jar` and the plugin zip to a GitHub Release `v0.1.0` with generated
  notes, creates the rolling `v0` release if it is not there, and replaces its jar;
- builds and pushes the image `ghcr.io/officefloor/sinew-slim` tagged `0.1.0`, `0.1`, `0` and
  `latest`;
- signs the plugin and uploads it to the JetBrains Marketplace (JetBrains then reviews it, so
  the version appears there a day or two later);

and when all three succeed, moves the `v0` tag to this commit. A failure in one of those three
(an outage, say) does not undo the others: fix it and re-run the failed jobs.

## One-time setup

### JetBrains Marketplace (the IntelliJ plugin)

A release needs four secrets: `PUBLISH_TOKEN` from the repository, and the signing trio from
the OfficeFloor organisation. If any is missing, the release fails at its first step, before
anything is published; `publishPlugin` also refuses to run without them, so the plugin is never
published unsigned.

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
