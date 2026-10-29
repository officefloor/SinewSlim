# SinewSlim

Slims Java methods that have grown past the length cap (26 code lines or more) by finding the
cheapest set of statement runs to extract. A run is cheap when little data crosses its boundary.
Where no run is cheap enough, the method is reported as one to redesign rather than split.

The design, and the plan for the codebase scan, the code-review gate and the IntelliJ plugin, is
in [DESIGN.md](DESIGN.md). Built so far: phases 1 to 4, the core, the codebase scan, the
code-review gate (git hook, GitHub Action, GitLab, Jenkins, Docker) and the IntelliJ plugin.

| Module | What it is |
|---|---|
| `core` | the facts model, the seam, the search, the assessment; no dependencies |
| `javaparser` | the facts of every method in a Java source file, from JavaParser's syntax tree |
| `config` | `.sinew-slim.yml`: defaults, profiles, validation, the effective-config dump |
| `cli` | the `sinew-slim` command: `scan`, `review`, `comment`, `install-hook`, `baseline`, `config` |
| `intellij` | the IntelliJ plugin: the inspection, the gutter mark, the proposal overlay, extraction |

## Review a change

`sinew-slim review` looks only at the methods a change touches. For each one that crosses the cap,
grows while over it, or is new and over it, it gives the proposal; for each that cannot be
slimmed, the redesign notice. A method that was already long and did not grow is information,
never a failure (the ratchet), so the gate can be switched on in a codebase that already has
long methods.

```bash
sinew-slim review                                  # the commit about to be made (staged)
sinew-slim review --mode worktree                  # uncommitted edits
sinew-slim review --mode range --base origin/main  # a branch, for CI
```

```
src/main/java/acme/Orders.java:48: Orders.place(Request) crossed the cap: 21 -> 31 lines; slimmable at cost 1 (1 piece, remainder 7 lines)
  lines 52-76   25 lines  in: session  out: -  static  saves 24
  remainder 7 lines (was 31)
sinew-slim: 1 method touched in 1 file against HEAD; 1 over the cap and newly so or longer (warn)
```

Exit codes: `0` ok or warn, `2` blocked (with `enforcement: block`), `1` an error. Formats:
`console` (the default; every finding starts `path:line:`, which IDE terminals link), `markdown`
(a PR comment), `json`, `sarif`, `github` (Actions annotations) and `codequality` (GitLab).
`--no-ratchet` gates on every touched method over the cap.

### As a git hook

```bash
sinew-slim install-hook          # .git/hooks/pre-commit: reviews the staged change on each commit
```

Under `enforcement: warn` (the default) it prints and the commit goes ahead; under `block` a
commit that grows a method over the cap is stopped (`git commit --no-verify` skips it once). For
the [pre-commit](https://pre-commit.com) framework:

```yaml
repos:
  - repo: https://github.com/officefloor/SinewSlim
    rev: v0.1.0
    hooks:
      - id: sinew-slim            # the Docker image; or sinew-slim-system for one on the PATH
```

### In GitHub Actions

```yaml
name: sinew-slim
on: pull_request
permissions:
  contents: read
  pull-requests: write            # for the sticky PR comment
jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0          # the base branch and the merge base
      - uses: officefloor/SinewSlim@v0
        with:
          enforcement: warn       # block when ready
```

The review goes to the job summary, a sticky PR comment (one, updated each run) and annotations
on the touched methods' lines. `code-scanning: true` also uploads SARIF (needs
`security-events: write`). The `verdict` output is `ok`, `warn` or `block`.

### In GitLab CI and Jenkins

[`ci/gitlab-ci.yml`](ci/gitlab-ci.yml) runs on merge-request pipelines with the published image,
posts a sticky MR note when a `GITLAB_TOKEN` (api scope) is set, and feeds the merge request's
code quality widget:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/officefloor/SinewSlim/v0/ci/gitlab-ci.yml'
```

[`ci/Jenkinsfile`](ci/Jenkinsfile) runs the image against `origin/${CHANGE_TARGET:-main}` and
archives the report. The image is `ghcr.io/officefloor/sinew-slim`:

```bash
docker run --rm -v "$PWD:/repo" ghcr.io/officefloor/sinew-slim:0 review --mode range --base origin/main
```

## Scan a codebase

```bash
./gradlew :cli:fatJar
java -jar cli/build/libs/sinew-slim.jar scan --repo path/to/repo
java -jar cli/build/libs/sinew-slim.jar scan --repo path/to/repo --format html --out scan.html
```

Every method gets one of five verdicts: under the cap, slimmable (with the pieces: their lines,
inputs, output, what they declare and whether they can be static), slimmable only by returning
a record, slimmable but every piece bound to the class, or not slimmable, with the reason. The
not-slimmable list is the one to look into for redesign.

| Option | |
|---|---|
| `--format` | `text` (default), `markdown`, `html`, `json` (every method, the contract), `csv`, `sarif` (GitHub code scanning) |
| `--out FILE` | write the report to a file |
| `--near N` | list methods under the cap within N lines of it (default 3) |
| `--config FILE` | the configuration (default `.sinew-slim.yml` at the root, if there) |
| `--profile study` | start from the study's values instead of SinewSlim's |
| `--trigger`, `--target`, `--min-piece`, `--max-inputs`, `--soft-inputs`, `--input-premium` | override one value |
| `--set KEY=VALUE` | override any key, as in the file: `--set framework.annotations=Next` |

`sinew-slim config` prints the effective configuration; `sinew-slim baseline` records every
method's length and status in `.sinew-slim-baseline.json`. In a git work tree the scan reads the
files git knows, so `.gitignore` is honoured.

## In IntelliJ

The plugin marks every method over the cap on its name and in the gutter, with its verdict. Click
the gutter mark, or Alt+Enter **Slim this method**, and the proposal is drawn over the method:
each piece highlighted, with what it takes and returns and two buttons. **Extract…** asks for
one thing, the new method's name, and extracts the piece through the IDE's own Extract Method,
moving any literal declarations into it first; **Skip** leaves the piece out and solves again.
It reads the project's `.sinew-slim.yml`, so it agrees with the hook and CI about every method.

```bash
./gradlew :intellij:buildPlugin     # intellij/build/distributions/sinew-slim-0.1.0.zip: Settings > Plugins > Install from Disk
./gradlew :intellij:runIde          # or try it in a sandbox IDE
```

IntelliJ IDEA 2024.2 or later, Community or Ultimate.

## Configure

`.sinew-slim.yml` at the repository root; every key has a default and an empty file is valid. An
unknown key or a value out of range is an error naming the key.

```yaml
trigger: 26              # a method with this many code lines or more is over the cap
target: 25               # every piece and the remainder must end at this many or fewer
min_piece: 3             # the smallest run worth extracting
max_inputs: 5            # a piece needing more parameters is not proposed
soft_inputs: 3           # each parameter beyond this costs input_premium more
input_premium: 2
framework:
  annotations: [Next]    # classes or methods read as functions a framework calls
exclude: ["**/generated/**", "**/target/**", "**/build/**"]
```

The full list, with every default, is what `sinew-slim config` prints.

```java
ParsedFile file = JavaFacts.parse("Orders.java", source);
for (MethodUnit m : file.methods()) {
    Assessment a = Assessor.DEFAULTS.assess(m.facts());
    // a.status(): UNDER_CAP, SLIMMABLE, SLIMMABLE_WITH_RECORD, SLIMMABLE_BUT_LOCKED, NOT_SLIMMABLE
    // a.proposal().cuts(): the pieces, each with its inputs, output and binding to the class
}
```

## Build

```bash
./gradlew test
```

Java 17 or later. The search recurses with the statement tree, so give the JVM a deep stack
(`-Xss64m`) for methods with long else-if chains.

## Parity with the study

The algorithm comes from the concentration-divergence study (`SPLIT.md`, `scripts/split.py`,
`measurer/.../LiveWidth.java`). Two checks hold the port to it:

- **The search.** `StudyParityTest` reads `split.py`'s answers, written by the study's
  `scripts/export_split_golden.py`, and requires the same result for every method under every
  variant (default, structural, no input limit, records, framework). A 1,200-method subset is
  committed and runs on every build. To check a full export:

  ```bash
  ./gradlew :core:test --tests '*StudyParityTest' -Dsinewslim.parity.full=/path/split-golden.jsonl.gz
  ```

- **The facts.** `LiveWidthParityTest` compares these facts with what the study's `LiveWidth`
  pass writes for the same source:

  ```bash
  java -cp divergence-measurer.jar net.officefloor.divergence.LiveWidth --root DIR --files LIST --out FACTS.json.gz
  ./gradlew :javaparser:test --tests '*LiveWidthParityTest' \
      -Dsinewslim.livewidth.facts=FACTS.json.gz -Dsinewslim.livewidth.root=DIR
  ```

Read on purpose differently from the study: a call to a static method of the class does not lock
a piece to it (the study, without telling static from instance, counted it), and the defaults are
SinewSlim's (pieces of 3 lines, up to 5 parameters). `Params.STUDY` restores the study's.
