# SinewSlim

Slims Java methods that have grown past the length cap (26 code lines or more) by finding the
cheapest set of statement runs to extract. A run is cheap when little data crosses its boundary.
Where no run is cheap enough, the method is reported as one to redesign rather than split.

The design, and the plan for the codebase scan, the code-review gate and the IntelliJ plugin, is
in [DESIGN.md](DESIGN.md). Built so far: **phase 1**, the core and the JavaParser facts, and
**phase 2**, the configuration and the codebase scan.

| Module | What it is |
|---|---|
| `core` | the facts model, the seam, the search, the assessment; no dependencies |
| `javaparser` | the facts of every method in a Java source file, from JavaParser's syntax tree |
| `config` | `.sinew-slim.yml`: defaults, profiles, validation, the effective-config dump |
| `cli` | the `sinew-slim` command: `scan`, `baseline`, `config` |

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
