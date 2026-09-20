---
name: "kotlin-seam-extraction"
description: "Splits large Kotlin files by extracting top-level types/functions into new same-package files without behavior changes. Invoke when refactoring oversized Kotlin files or asked to split/extract classes, objects, or composables in this workspace."
---

# Kotlin Seam Extraction

Behavior-preserving file splitting for Kotlin/Android projects. Extracts cohesive
top-level declarations (classes, objects, enums, top-level functions) out of
oversized files into focused files **within the same package**, so no visibility
changes are needed.

## When to use

- A file is large and contains 2+ top-level types with separate responsibilities.
- User asks to split / extract / refactor a large Kotlin file.

Do NOT use this to split a single class or a single giant `@Composable` with
shared private mutable state — that requires state-delegation rewrites and is a
separate, riskier task. Only act on clean seams.

## Workflow

### 1. Find candidate files

List largest files and count top-level type declarations per file:

```bash
find app/src/main/java -name '*.kt' -not -path '*/build/*' | xargs wc -l | sort -rn | head -15

for f in $(find app/src/main/java -name '*.kt'); do
  n=$(grep -cE '^(internal |private |public |abstract |open |sealed |data |enum )*(class|object|interface) ' "$f")
  [ "$n" -ge 2 ] && echo "$n  $(wc -l < "$f")L  $f"
done | sort -k2 -rn
```

### 2. Validate the seam BEFORE cutting (all must hold)

- **No private coupling**: the moving block must not reference `private`
  declarations that stay behind. Grep the moving range for private symbols.
- **Same package** as the origin (preferred) → no visibility edits required.
- **Cross-file consumers**: `grep -rln 'TypeName' <srcroot>` to confirm the
  type is independently used elsewhere (evidence the extraction is coherent).
- **Top-level helpers**: private file-level extension functions are usable from
  any file in the same package, but decide whether they move with the block or
  stay; grep their call sites first.
- **KDoc boundaries**: comments (`/** ... */`) often start on a line BEFORE the
  `class`/`fun` keyword and their closing `*/` may sit inside the range. Print
  ~6 lines around every cut point with `sed -n 'A,Bp' | cat -n` so a doc opener
  and closer never land in different files.

### 3. Curate imports — never copy the whole import block

For each import in the origin, count real usages in the fragment body
(excluding import lines):

```bash
for sym in Foo Bar; do echo "$sym -> $(grep -vnE '^import ' file.kt | grep -c "\b$sym\b")"; done
```

Build the new file header as `package` + only the imports the fragment needs.
Conversely, re-check the origin after removal and delete imports whose only
user moved out (wildcard imports like `java.io.*` can stay).

### 4. Cut with line ranges

```bash
# new file = header + moving range; origin = keep prefix + keep suffix
{ head -n N   "$f"; sed -n 'A,Bp' "$f"; } > "$dir/NewFile.kt"
{ head -n P   "$f"; sed -n 'M,$p' "$f"; } > "$f.tmp" && mv "$f.tmp" "$f"
```

For a simple prefix extraction where the origin ends before the block, the
origin can just be `head -n P`.

### 5. Verify (must be green before committing)

```bash
./gradlew :app:testDebugUnitTest --console=plain
```

- A first run can report `Configuration cache entry discarded` because a
  `git rm`/move changes the output of Gradle's `git` external-process probe —
  this is a transient cache miss, not a compile error. Re-run; a clean
  `BUILD SUCCESSFUL` confirms it.
- Real syntax errors usually mean a straddled KDoc (dangling `*/` or `/**`);
  inspect the head/tail of both files.
- Missing-import errors (e.g. `Unresolved reference 'roundToInt'`) mean step 3
  missed a symbol; add the import to the new file and drop it from the origin
  if now unused.

### 6. Commit only when the user asks

Stage explicitly (`git add app`), keep the commit message factual, and list each
extraction as `Origin -> NewFile (responsibility)`. Never amend, never force-push.

## Stop conditions

- The only remaining large files are single classes / single composables with
  private state → stop and report; do not force splits.
- Models smaller than ~50 lines or tightly cohesive model groups (e.g. a sealed
  type plus all its cases) are usually better left co-located.
- Vendored third-party packages (e.g. `com.termux.*`) are off-limits except for
  references that must follow a rename.
