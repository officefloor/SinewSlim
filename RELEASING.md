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
# 1. Set version= in gradle.properties, commit it.
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
- moves the `v0` tag to this commit.

## One-time setup

### GHCR (the Docker image)

No secret: the `docker` job pushes with the built-in `GITHUB_TOKEN`. **The package must be
public**: the GitLab and Jenkins templates and the pre-commit hook pull
`ghcr.io/officefloor/sinew-slim:0` anonymously. After the first release, make it public at
https://github.com/orgs/officefloor/packages/container/sinew-slim/settings (Danger Zone >
Change visibility; public packages must be allowed at the organisation level first). Check with
an anonymous pull:

```bash
docker logout ghcr.io
docker pull ghcr.io/officefloor/sinew-slim:0
```

### The repository

The Action and the release assets are only reachable by others once the repository is public.

### GitHub Marketplace (optional)

Each release creates a GitHub Release, which is where a Marketplace listing lives. The first
listing is manual: edit the Release, tick "Publish this Action to the GitHub Marketplace", and
accept the agreement. Later releases can be published from the same screen.
