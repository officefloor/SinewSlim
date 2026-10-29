# SinewSlim

**Long methods, slimmed at the right seams.**

Every linter can tell you a method is too long. SinewSlim tells you **where to cut it**: the
exact runs of statements to extract, chosen so the fewest values cross into and out of each new
method. You type a name, and your IDE does the rest. When no clean cut exists, it says so, and
why, because that method needs a redesign rather than a split.

```
src/main/java/acme/Orders.java:48: Orders.place(Request) crossed the cap: 21 -> 31 lines; slimmable at cost 1
  lines 52-76   25 lines  in: session  out: -  static  saves 24
  remainder 7 lines (was 31)
```

## Why 26 lines

Because that is where code reviewers draw the line. In a study of reviewer-labelled Java code
([*What reviewers call a Long Method is a length*](https://doi.org/10.5281/zenodo.23749773)), a
plain length of 26 code lines picked out the methods reviewers flagged as Long Methods better
than any complexity, coupling or variable-count measure. So SinewSlim does not argue about
whether a method is too long. It gets on with making it shorter.

## What you get

- **A plan, not a complaint.** For every method of 26 lines or more, the cheapest set of pieces
  that brings it, and every piece, back to 25 or fewer: each piece's lines, its parameters, its
  return value, and whether it can be `static`.
- **Cuts a developer would make.** A piece is cheap when little data crosses its boundary, so the
  proposals follow the natural seams of the method instead of chopping it every 25 lines.
- **An honest "don't".** Some methods cannot be split cleanly: every cut carries two results,
  needs too many parameters, or is blocked by early returns. SinewSlim names the reason and flags
  the method for redesign, so nobody wastes an afternoon on a refactoring that makes it worse.
- **God-class detection for free.** When every piece of a long method would still have to live
  in its class, the method is not the problem. The class is, and SinewSlim says so.
- **Safe to switch on in old code.** In code review it only gates on methods a change makes
  longer, or pushes over the cap. Existing long methods never fail a build; they just stop
  growing.
- **One answer everywhere.** The IDE, the git hook and CI read the same `.sinew-slim.yml` and run
  the same engine, so they never disagree about a method.

## In IntelliJ IDEA

Every method over the cap is marked on its name and in the gutter. Click the gutter icon, or
press **Alt+Enter → Slim this method**, and the plan is drawn right over your code:

```
SinewSlim · 89 lines · slimmable at cost 1 (5 pieces, remainder 19 lines)            [Close]
┃ piece 1 · lines 1219-1235 · 12 lines · in: officeBuilder · out: - · static if passed: teams, context · saves 11   [Extract…] [Skip]
      ...the piece's code, highlighted...
┃ piece 2 · lines 1243-1275 · 23 lines · in: officeBindings · out: - · static if passed: context, objects, managedObjectSources · saves 22   [Extract…] [Skip]
```

- **Extract…** asks for one thing, the new method's name. IntelliJ's own Extract Method does the
  edit, so types, generics, `throws` clauses and `static` are all handled. Ctrl+Z undoes it.
- **Extract with record…** handles a piece that produces two values: it declares a record,
  returns it, and unpacks it for you.
- **Skip** drops a piece you don't like and re-plans the method without it.

Works with IntelliJ IDEA 2024.2 and later, Community and Ultimate. Download
`sinew-slim-<version>.zip` from [Releases](https://github.com/officefloor/SinewSlim/releases),
then **Settings → Plugins → ⚙ → Install Plugin from Disk…**.

## In your pull requests

Add the GitHub Action and every pull request gets reviewed for long methods:

```yaml
name: sinew-slim
on: pull_request
permissions:
  contents: read
  pull-requests: write
jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0
      - uses: officefloor/SinewSlim@v0
```

- **A single PR comment**, updated on every push, with the plan for each method the change made
  too long, and a redesign notice for each one that can't be slimmed.
- **Annotations** on the methods' lines, right in the diff.
- **Your choice of strictness.** It only reports by default. Set `enforcement: block` and the
  check fails when a change grows a method over the cap; make it required to gate merges.

## Everywhere else

- **Git hook:** `sinew-slim install-hook` reviews each commit before it is made. Or use the
  [pre-commit](https://pre-commit.com) framework:

  ```yaml
  repos:
    - repo: https://github.com/officefloor/SinewSlim
      rev: v0.1.0
      hooks:
        - id: sinew-slim
  ```

- **GitLab CI:** include [`ci/gitlab-ci.yml`](ci/gitlab-ci.yml) for a merge-request note and the
  code quality widget.
- **Jenkins:** [`ci/Jenkinsfile`](ci/Jenkinsfile).
- **Any CI with Docker:**

  ```bash
  docker run --rm -v "$PWD:/repo" ghcr.io/officefloor/sinew-slim:0 review --mode range --base origin/main
  ```

- **The whole codebase at once:** `sinew-slim scan --format html --out scan.html` gives a
  browsable report of every method: which are slimmable and how, which need redesign, and which
  classes are turning into god classes. Also as Markdown, JSON, CSV and SARIF.

The command line is a single jar on [Releases](https://github.com/officefloor/SinewSlim/releases)
(`java -jar sinew-slim.jar`, Java 17 or later), with the `bin/sinew-slim` launcher.

## Make it yours

Everything is a setting in `.sinew-slim.yml` at the root of your repository, shared by the IDE,
the hook and CI. All of it is optional:

```yaml
trigger: 26              # a method this long or longer is over the cap
target: 25               # every piece, and what's left, must end this short
min_piece: 3             # the smallest piece worth extracting
max_inputs: 5            # never propose a piece with more parameters than this
soft_inputs: 3           # prefer pieces with this many parameters or fewer...
input_premium: 2         # ...by charging this much for each one over
enforcement: warn        # or block, to fail the check when a change grows a method
exclude: ["**/generated/**"]
```

A mistyped key is reported by name instead of being quietly ignored.
`sinew-slim config` prints every setting with its default.

## License

[Apache License 2.0](LICENSE). Built by [OfficeFloor](https://officefloor.net). How it works,
and the research behind it, is in [DESIGN.md](DESIGN.md).
