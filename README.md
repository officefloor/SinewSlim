# SinewSlim

Slims Java methods that have grown past the length cap (26 code lines or more) by finding the
cheapest set of statement runs to extract. A run is cheap when little data crosses its boundary.
Where no run is cheap enough, the method is reported as one to redesign rather than split.

The design, and the plan for the codebase scan, the code-review gate and the IntelliJ plugin, is
in [DESIGN.md](DESIGN.md). Built so far: **phase 1**, the core and the JavaParser facts.

| Module | What it is |
|---|---|
| `core` | the facts model, the seam, the search, the assessment; no dependencies |
| `javaparser` | the facts of every method in a Java source file, from JavaParser's syntax tree |

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
