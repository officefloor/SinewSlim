# SinewSlim — design

> **Status (29 October 2026). DESIGN, NOT YET BUILT.** The build-out of
> `concentration-divergence-study/SPLIT-TOOL.md`. The algorithm is the one in that study's
> `SPLIT.md`, run today by `scripts/split.py` over the facts from `measurer/.../LiveWidth.java`.
> This document says how it becomes a product: a codebase report, a code-review gate and an
> IntelliJ plugin, all in Java, all driven by one configuration.

## The name

A sinew is the data that has to cross a seam when a method is cut in two (the study's
`SINEW.md`). A long method is a muscle grown too big. SinewSlim does not cut the sinew
through; it slims the method down to the cap by moving whole runs of statements out at the
seams where the least sinew crosses. Where no such seam exists, the sinew runs through the
whole method, and that is the method to redesign rather than split.

## What it does, in one paragraph

A method of 26 code lines or more is long; the study's reviewers call it a Long Method at that
length and nothing else beats it (step 13, MCC 0.777). SinewSlim does not re-argue that. For
every long method it finds the cheapest set of contiguous statement runs whose extraction
brings the method, and every extracted piece, to 25 lines or fewer, where the cost of a piece
is what crosses its boundary: its parameters and its return value. It says three things about
a method: **under the cap**; **slimmable**, with the pieces and their cost; or **not
slimmable**, with the reason, which is the warning list a developer should look into as a
design problem. The three surfaces below are three ways of showing those answers.

## Surfaces

| # | Surface | Scope | Output |
|---|---|---|---|
| 1 | **Scan** (`sinew-slim scan`) | the whole codebase | a report of every method: length, whether it is over the cap, the slim cost of the over-cap ones, the not-slimmable list, the locked share per class |
| 2 | **Review** (`sinew-slim review`) | the methods a change touches | the proposal for each touched method over the cap, the redesign warning for each that cannot be slimmed; as a GitHub Action, a GitLab job, a Jenkins step, a git hook, or console text for an IDE terminal |
| 3 | **IntelliJ plugin** | the method under the cursor | the pieces drawn over the method, each with a button that extracts it after asking only for a name |

All three are thin shells over one pure core. The same configuration file drives all three,
so the IDE, the hook and CI never disagree about a method.

## Language and build

Java throughout, no Python. The study's `split.py` is ported, not wrapped.

- **Java 17** as the language level (the IntelliJ platform's floor, and the measurer's).
- **Gradle** (Kotlin DSL) for every module, since the IntelliJ plugin needs the IntelliJ
  Platform Gradle plugin (2.x, as in `~/OfficeFloor-IntelliJ-Plugin`) and one build tool is
  better than two.
- **JavaParser** (pinned, 3.28.x as the measurer) parses the Java outside the IDE. No symbol
  solver: the pass reads syntax and lexical scope only, which is what makes it fast enough for
  a hook.

### What is a library, and what is ours

SinewSlim parses nothing itself. The parsing, the grammar across Java versions, and the syntax
tree are JavaParser's outside the IDE and IntelliJ's PSI inside it, and both are maintained
upstream.

What is ours is the layer between the tree and the search: walking the tree to record each
variable's reference, definition and mutation lines, the loop extents and the jumps. No
library offers this from source. The candidates were checked:

| Library | Why not |
|---|---|
| JavaParser symbol solver | resolves names and types; no def-use or data flow |
| Spoon | a richer tree, but its data-flow module is experimental and unmaintained |
| PMD 7 | dropped its data-flow analysis in version 7 |
| Soot / SootUp, WALA, Checker Framework dataflow | work on bytecode or need `javac` to compile the code first: unusable on a staged hook, on unsaved editor text, or on a repository that does not build |
| IntelliJ `ControlFlowUtil` (`getInputVariables`, `getOutputVariables`) | gives exactly a range's inputs and outputs, but only inside the IDE |

So the facts layer stays, but it is not new code. It is the study's `LiveWidth.java`, already
Java and already run over the study's corpora, taken over with the study-only output removed
(about 800 lines today, less once trimmed). What is ported from Python is only the search,
`split.py`.

Inside the IDE, the plugin cross-checks: for each proposed piece it asks IntelliJ's
`ControlFlowUtil` for the range's inputs and outputs and logs any disagreement with the facts
layer. Each disagreement is a test case for the facts layer, so the IDE keeps it honest
without SinewSlim depending on the IDE everywhere else.

```
SinewSlim/
  settings.gradle.kts
  core/        pure: the facts model, the seam, the search, the cost, the result. No I/O, no parser.
  javaparser/  facts from JavaParser's syntax tree (the study's LiveWidth.java, taken over)
  config/      .sinew-slim.yml: load, defaults, validation, the effective-config dump
  cli/         scan, review, hook, comment; git; report formats; providers (GitHub, GitLab)
  intellij/    the plugin: inspection, overlays, extraction through the IDE
  ci/          gitlab-ci.yml, Jenkinsfile (as ImpactGate)
  action.yml   the GitHub Action
  Dockerfile   JRE + git + the CLI jar
  doc/         the algorithm, the configuration, each surface
```

`core` depends on nothing. `javaparser` and the plugin each turn source into the same `core`
facts; everything above that line is shared. That is the one rule that keeps the three
surfaces agreeing.

## Configuration

One file at the repository root, `.sinew-slim.yml`, found by every surface (the plugin reads
it from the project root, and falls back to the IDE settings page when there is none). CLI
flags and Action inputs override it. Every value has a default; an empty file is valid.

```yaml
# the cap
trigger: 26              # a method with this many code lines or more is long
target: 25               # every piece and the remainder must end at this many or fewer

# a piece
min_piece: 3             # the smallest run worth extracting, in code lines
min_saving: 2            # lines a cut must remove (the run less its one call line)
max_outputs: 1           # values a piece may return; Java has one
trailing_return: true    # a run may end in a return; any other jump out of it is fatal

# parameters: a hard limit, and an optional soft limit with a premium above it
max_inputs: 5            # a piece needing more is not proposed
soft_inputs: null        # e.g. 3: each parameter beyond this costs extra
input_premium: 2         # the extra cost per parameter beyond soft_inputs

# cost weights
output_weight: 1         # a single return costs the same as a parameter
piece_cost: 1            # a name and a call site per piece

# variants, reported beside the default proposal
records: true            # when nothing is feasible with one return, try records (3 per value)
structural: true         # also solve with whole if/loop/try/switch statements only
prefer_structural_within: 1   # prefer the structural proposal when it costs at most this much more

# binding to the class
lock_on_any_field: false # default: a field write or an own-method call locks a piece
parameterise_field_reads: true

# framework reading (function injection): which classes are read this way
framework:
  annotations: []        # e.g. [net.officefloor.plugin.section.clazz.Next]
  free_params: true

# what is scanned
include: ["**/*.java"]
exclude: ["**/generated/**", "**/target/**", "**/build/**"]

# gating (review only)
enforcement: warn        # off | warn | block
ratchet: true            # block only on growth (see Review)
```

### The defaults, and where they differ from the study

| Setting | SinewSlim default | Study (`SPLIT.md`) | Why |
|---|---|---|---|
| trigger / target | 26 / 25 | 26 / 25 | the step 13 cutoff, unchanged |
| min_piece | 3 | 5 | small helpers are proposed; the study's 5 came from Alon and Feitelson's distribution, not a rule |
| min_saving | 2 | 3 | follows from min_piece: a 3-line run replaced by a call saves 2 |
| max_inputs | 5 | 3 | a hard ceiling only; the soft limit carries the preference instead |
| max_outputs | 1 | 1 | Java's single return |

`sinew-slim config --profile study` prints and applies the study's values, so a run can be set
beside the paper's numbers.

### The soft parameter limit

The hard limit says which pieces may be proposed at all. The soft limit says which are
preferred: a piece with `k` inputs costs

```
inputs_cost(k) = k + input_premium * max(0, k - soft_inputs)      (soft_inputs set)
inputs_cost(k) = k                                                 (soft_inputs null)
```

With `soft_inputs: 3, input_premium: 2`, a 4-parameter piece costs 6 and a 5-parameter piece
costs 9, so the search takes a 4-parameter cut only when every 3-parameter route costs more.
The premium enters the seam cost below, so it changes both the ranking and the worst seam.

## The core

### Facts

Per method, from either parser:

- the **statement tree**: every node with its kind (block, if, loop, try, switch entry,
  lambda body, return, break, continue, throw, yield, other) and its line span;
- the **code lines**: lines inside the method that hold code, not blank and not only
  comments; this is the length the cap is read on (the oracle's nloc);
- per **local and parameter**: its reference lines, definition lines, mutation lines (a
  write into the object: an array store, a field set through it), the loop extents it sits
  in, and whether its declaration has no value or a literal one;
- per **field**: the lines it is read on and written on;
- per **call**: its line, and whether it has no receiver or `this` as receiver (an own-class
  call).

The facts are the study's live-width facts, schema 3, as a Java record set rather than JSON.

### The seam

For a run of statements with line set L, each local or parameter takes one role:

| Role | Rule | Becomes |
|---|---|---|
| input | referenced in L and referenced before L | a parameter |
| output | written in L and referenced after L, or referenced earlier in an enclosing loop | the return value |
| moved | declared before L with no value or a literal one, every other reference in L | nothing: the declaration moves into the new method |
| passed | a method parameter used unchanged | an input, labelled; free under the framework reading |
| internal / untouched | — | nothing |

A run is **feasible** when it has at most `max_outputs` outputs, at most `max_inputs`
inputs, no jump that leaves it (a trailing `return` aside), at least `min_piece` lines, and
at most `target` lines once its own inner pieces are out.

```
seam(p)  = inputs_cost(inputs_p) + output_cost(outputs_p)
output_cost(0) = 0,  output_cost(1) = output_weight,  output_cost(k ≥ 2) = 3k   (records variant)
cost(S)  = Σ_p ( seam(p) + piece_cost )
```

### The search

The bottom-up dynamic program of `split.py`, ported:

1. For each sequence (block, switch entry, lambda body), walk its statements in order. At
   each statement either keep it in the remainder (with any reduction its inner sequences
   offer) or close an extracted run ending there.
2. Keep a Pareto front per state: remainder length so far, worst seam, pieces, total cost.
3. A statement longer than the target cannot move whole; its inner sequences are solved
   first, and the parent sees the lengths it can be reduced to and what each costs.
4. Of the segmentations where the remainder and every piece end at or under the target, take
   the one with the smallest **worst seam**, then the **fewest pieces**, then the smallest
   **total cost**.

Then the variants: **structural** (whole `if`, loop, `try`, `switch` statements only, preferred
when within `prefer_structural_within` of the best, since that is where developers cut), and
**records** (only when the default is infeasible).

### The result

```
MethodResult
  location        file, class, method signature, line span
  length          code lines
  status          UNDER_CAP | SLIMMABLE | SLIMMABLE_WITH_RECORD | NOT_SLIMMABLE
  slimCost        the worst seam of the best segmentation
  totalCost       cost(S)
  interface       the sum of seams: the sinews the slimming creates
  pieces[]        line range, length, inputs (passed marked), output, moved declarations,
                  binding (free | bound by reads: fields | locked: reason), lines saved
  remainder       length after, binding
  reason          for NOT_SLIMMABLE: the obstacle (below)
  structural      the structural-only result beside it
```

**The reason for NOT_SLIMMABLE** is chosen from what blocked the search, most specific first:

- *every cut carries two results* (outputs, at every candidate seam);
- *every cut needs more than 5 parameters* (inputs);
- *the loop at 52–88 cannot be shortened without a second output* (a single statement over
  the target with no feasible inner reduction);
- *a `break`/`continue` leaves every candidate run* (jumps);
- *every piece is locked to the class* (all pieces feasible but every one, and the remainder,
  writes fields or calls the class's own methods): reported as a god-method warning even
  though a split exists, because splitting it moves nothing out of the class.

The last one is a status of its own in the report (`SLIMMABLE_BUT_LOCKED`), since the fix is
the class, not the method.

### Performance

The search is per method and independent, so the scan runs methods in parallel on a
`ForkJoinPool`. The study ran it over every method of the MLCQ corpus in Python; in Java a
large repository should be dominated by parsing. A per-file cache keyed by the file's blob
hash (`.sinew-slim/cache/`) lets repeated scans and hooks skip unchanged files.

## Surface 1 — Scan

```bash
sinew-slim scan [--repo .] [--format text|markdown|html|json|csv|sarif] [--out report]
```

The report has four parts:

1. **Summary.** Methods scanned; how many under the cap; how many over it, split into
   slimmable, slimmable only with a record, slimmable but locked, not slimmable; the
   distribution of slim cost among the slimmable; the total lines over the cap (the excess,
   `Σ max(0, length − target)`).
2. **Under the cap.** Every method with its length and headroom (`target − length`), so the
   methods close to the cap show before they cross it. Collapsed in text formats, full in
   JSON and CSV.
3. **Over the cap, slimmable.** Ranked by slim cost, then length: each method with its cost
   and its proposal (pieces, inputs, output, binding, lines saved).
4. **The warning list: not slimmable.** Each method with its reason, length and class. These
   are the methods a developer should look into for redesign; the report says so in words:
   *this method is bound to the object's state; the next change here is a design decision,
   not an edit.*

Plus a **class table**: per class, methods over the cap, pieces proposed, the **locked share**
(locked pieces over all pieces), and the not-slimmable count. A high locked share is a god
class rather than a set of long methods, and the report sorts by it.

The JSON is the contract (versioned schema, every parameter written into it) that the other
formats and later tools read. `sarif` lets GitHub code scanning show the not-slimmable list as
alerts.

`sinew-slim baseline` writes the scan's per-method lengths and statuses to
`.sinew-slim-baseline.json`, which the review's ratchet reads when there is no base commit to
compare with.

## Surface 2 — Review

```bash
sinew-slim review --mode staged|worktree|range [--base origin/main]
                  [--format console|markdown|json|sarif] [--enforcement off|warn|block]
```

The modes and the plumbing follow ImpactGate (`~/ImpactGate`): `staged` (index against
`HEAD`, the hook default), `worktree` (unstaged edits), `range` (merge-base of `--base` to
`HEAD`, for CI). Git is driven as a subprocess, so the Docker image needs only a JRE and git.

**What it looks at.** Each changed `.java` file is parsed at the head side, and at the base
side when the file existed. A method is **touched** when a changed hunk overlaps its span.
Methods are matched across the two sides by class and signature, falling back to name when
the signature changed. Nothing is said about a method the change did not touch.

**What it says**, per touched method:

| Before → after | Message | Gates (ratchet on) |
|---|---|---|
| under → under | nothing | — |
| under → over | *crossed the cap*, with the proposal | yes |
| over → longer | *grew from 31 to 38*, with the proposal | yes |
| over → same or shorter | *still over the cap*, with the proposal, as information | no |
| any → not slimmable | *cannot be slimmed: {reason}; investigate for redesign* | yes, if it was touched and is over the cap |
| new method over the cap | *new method over the cap*, with the proposal | yes |

With `ratchet: false`, every touched method over the cap gates. The ratchet is what makes the
gate survivable on a codebase that already has long methods: nothing that was long before
fails, only making it longer does.

Exit codes as ImpactGate: `0` ok or warn, `2` blocked, `1` usage or environment error.

### Console output (hook and IDE terminals)

Lines start with `path:line:` so IntelliJ, VS Code and most terminals turn them into links:

```
src/main/java/acme/OrderService.java:48: placeOrder grew 31 -> 38 lines (cap 25); slim cost 2
  lines 52-63   12 lines   in: request, config   out: session   static            saves 11
  lines 70-78    9 lines   in: session           out: -         instance: cache   saves 8
  remainder     21 lines
src/main/java/acme/Ledger.java:112: reconcile is 44 lines and cannot be slimmed:
  every cut carries two results (total, adjustments). Investigate for redesign.
sinew-slim: 2 methods over the cap, 1 not slimmable (warn)
```

### Distribution

As ImpactGate, with the Java toolchain in place of pip:

- **GitHub Action** (`action.yml`, composite): `actions/setup-java` (Temurin 17), download the
  released CLI jar (cached by version), run `review --mode range` against the PR base, write
  the markdown to the job summary, post or update a **sticky PR comment**, and upload SARIF
  when `code-scanning: true` so the proposals appear as annotations on the PR's lines.
  Inputs mirror the config keys. `fetch-depth: 0` is required.
- **Docker image** `ghcr.io/officefloor/sinew-slim` (Temurin 17 JRE, git,
  `safe.directory '*'`, non-root): the base for GitLab and Jenkins and for anyone without Java.
- **GitLab** (`ci/gitlab-ci.yml`): merge-request rule, `GIT_DEPTH: 0`, `review --base
  $CI_MERGE_REQUEST_DIFF_BASE_SHA`, sticky MR note when `GITLAB_TOKEN` is set, report as an
  artifact; optionally the GitLab code-quality JSON format so findings show in the MR widget.
- **Jenkins** (`ci/Jenkinsfile`): `docker run` the image against
  `origin/${CHANGE_TARGET:-main}`, archive the report.
- **git hook**: `sinew-slim install-hook` writes `.git/hooks/pre-commit` running `review
  --mode staged --format console`. For the pre-commit framework, a `.pre-commit-hooks.yaml`
  entry using `language: docker_image` (pre-commit has no Java language), and a `system`
  entry for teams with the CLI installed.
- **The CLI itself**: a single shaded jar on GitHub Releases and Maven Central, plus a small
  launcher script. JVM start-up is the cost a hook feels; a GraalVM native image is the later
  option if it matters, and the core's lack of reflection keeps that open.

`sinew-slim comment --provider github|gitlab --body-file report.md` posts the sticky comment,
as ImpactGate's command does.

## Surface 3 — IntelliJ plugin

### What the developer sees

- **Inspection**: a method at or over the trigger gets a weak warning on its name, with the
  status in the text: *38 lines; slimmable at cost 2 (2 pieces)*, or *44 lines; cannot be
  slimmed: every cut carries two results*. The not-slimmable case uses a stronger highlight
  and its own wording, since it is the redesign notice.
- **Gutter mark** on long methods; clicking it, or the intention *Slim this method*
  (Alt+Enter on the name), turns on the **overlay**.
- **Overlay**: each proposed piece is a coloured band over its lines (range highlighters,
  colours from the editor scheme so light and dark both work), with a block inlay above it
  holding the one-line summary and two buttons:

  ```
  ┌ piece 1 · 12 lines · in: request, config · out: session · static · saves 11   [Extract…] [Skip]
  ```

  The remainder gets a summary at the method's head: *remainder 21 lines · instance: calls
  validate · 2 of 3 pieces stay in the class*. Esc or the gutter mark turns the overlay off.
- **Extract…** asks for one thing, the name, in a small popup with a suggestion (camel-cased
  from the output variable, or empty). Enter performs the extraction; nothing else is asked.
- **Skip** drops the piece and re-solves the rest without it, since the cheapest segmentation
  without that run may be different. The overlay redraws.
- **Records variant**: when the proposal needs a record, the piece's summary says so and its
  popup asks for two names, the method's and the record's. This is the one case the plugin
  writes code the IDE's Extract Method does not (below).
- **Framework reading** classes show the declares/produces form of `SPLIT-TOOL.md` as a
  diagnostic only, with no Extract button, until a framework-aware refactoring exists.

### How it works

- **Facts from the editor's own file.** The plugin runs the same `javaparser` module over the
  document text, not PSI, so the IDE and CI compute the identical proposal for the identical
  text. Offsets map back to the document by line and column. PSI is used for what JavaParser
  cannot do: applying the edit. The parse runs in a `ReadAction.nonBlocking` on document
  change, debounced, per method, and is cancelled on further typing.
- **Applying a piece**, one at a time, in one command each so each undoes on its own:
  1. move the piece's literal declarations down into its range (a PSI edit inside the same
     command), so the IDE does not turn them into parameters;
  2. select the piece's PSI statements and run IntelliJ's own Extract Method with the method
     name preset and the dialog suppressed. The platform's entry points are
     `ExtractMethodProcessor` (`prepare()`, `setMethodName`, `doRefactoring()`) and, in newer
     platforms, `com.intellij.refactoring.extractMethod.newImpl.MethodExtractor`; both are
     implementation classes, not stable API, so a first spike pins which one to use for the
     supported platform range and isolates it behind one small adapter;
  3. let the IDE decide static or instance, the parameter types, the `throws` clause, and the
     generics; it is the authority on everything the syntactic facts cannot see;
  4. re-solve the method and redraw.
- **If the IDE refuses** the region (it finds two outputs the facts missed, or a resolution the
  lexical match got wrong), the plugin shows the IDE's reason on the piece, marks the run
  excluded, re-solves, and logs the refusal: a refusal is a defect in the facts, and the log is
  how they get fixed.
- **Records**: when a piece needs a record, the plugin creates a local record from the output
  names and types (types from PSI), rewrites the piece's tail to return it and the call site to
  unpack it, then runs the extraction. This is later work (phase 5); until then the records
  proposal is shown without the button.
- **Settings**: read from `.sinew-slim.yml` in the project root and watched for changes; a
  settings page edits the IDE-only defaults for projects without the file, and shows the
  effective configuration.
- **Platform**: IntelliJ Platform Gradle plugin 2.x, `com.intellij.java` bundled plugin, a
  `sinceBuild` that the extract-method spike settles, Plugin Verifier in CI, signed and
  published to the JetBrains Marketplace as `~/OfficeFloor-IntelliJ-Plugin` is.

## What the tool is not

- **Not a smell detector.** Length over the cap is the trigger. The tool acts on it.
- **Not an automatic refactorer.** In the plugin every edit goes through the IDE's
  refactoring with the developer's name on it. The CLI never edits code.
- **Not a judge of cohesion.** It costs interface, not meaning. A cheap piece can still be the
  wrong thing to name; the developer skipping it is the answer.

## Testing

- **Parity with the study.** The Java core is accepted when it reproduces `split.py` exactly
  (status, slim cost, pieces, cuts) on the study's facts for every method in the MLCQ and
  OfficeHQ sets, under the study profile. The study's facts files are the golden data; the
  comparison runs once in the study repository and its expected outputs are committed here as
  test resources, so this repository needs no Python.
- **Unit tests** in `core` from hand-written fact sets: each seam role, each jump rule, the
  soft-limit premium, the loop back edge, recursion into a long loop, every not-slimmable
  reason.
- **Fixture sources** in `javaparser` and `intellij`: small Java files with the expected
  proposal in a comment, run through both fact producers, which must agree.
- **IntelliJ tests** on the platform's light fixtures: overlay ranges, extraction of each
  piece in fixture files, refusal handling, undo.
- **CLI tests** against throwaway git repositories: each mode, the ratchet table above, exit
  codes, each output format.

## Build order

| Phase | Delivers | Done when |
|---|---|---|
| 1 | `core` + `javaparser`: the facts, the search, the result | parity with `split.py` on the study's sets |
| 2 | `config` + `cli scan` in every format | a scan of OfficeFloor and of a MLCQ repository in reasonable time; the not-slimmable list read by hand |
| 3 | `cli review`, hook, Docker, Action, GitLab, Jenkins | a PR in a test repository gets the sticky comment, annotations and the right exit code in each CI |
| 4 | IntelliJ: inspection, overlay, extract with name only, skip and re-solve | a long method slimmed under the cap in the IDE with only names typed |
| 5 | records extraction, framework reading, native image | as each is needed |

## Open questions

- **Do developers accept the proposals?** Haas and Hummel's 86% is the bar. The plugin's
  skip and refusal logs, with the user's consent, are how it would be measured.
- **Does the soft limit beat the hard one?** With the study's `max_inputs: 3` as the soft limit
  under a hard 5, are the extra proposals (4 and 5 parameters) accepted as often?
- **Line counting outside Java.** Everything here is Java. Kotlin is the obvious next language
  for an IntelliJ-centred tool, and needs its own fact producer (no JavaParser), so it is a
  separate piece of work rather than a setting.
- **Licence and coordinates.** Apache-2.0 as ImpactGate; Maven group `net.officefloor.sinewslim`;
  CLI name `sinew-slim`; image `ghcr.io/officefloor/sinew-slim`; Action
  `officefloor/SinewSlim@v0`.

## Sources

- `concentration-divergence-study/SPLIT.md`: the algorithm, the parameters and the prior work.
- `concentration-divergence-study/SPLIT-TOOL.md`: the three signals and the surfaces this
  builds out.
- `concentration-divergence-study/scripts/split.py` and `measurer/.../LiveWidth.java`: the
  implementation to port.
- `~/ImpactGate`: the CLI modes, the Action, the GitLab and Jenkins templates, the hook, the
  Docker image and the release flow to follow.
- `~/OfficeFloor-IntelliJ-Plugin`: the IntelliJ Platform Gradle setup, signing and publishing.
