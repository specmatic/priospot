# PrioSpot Language-Agnostic Specification (C3-Based, JSON-First)

## 1. Document Purpose
This specification defines the exact requirements for implementing PrioSpot, a language-agnostic replacement of the current C3 pipeline.

The implementation must:
1. Preserve current C3 computation semantics.
2. Replace internal XML persistence with JSON as the canonical format.
3. Produce equivalent report outputs (especially interactive treemap SVGs).
4. Be deterministic in baseline mode for stable CI comparison.
5. Be integrable into host build systems through one top-level `priospot` command/task.

## 2. Goals
1. Build PrioSpot as a standalone engine that ingests existing quality metrics and computes hotspot priority from the C3 formula.
2. Use JSON (`priospot.json`) as the canonical model format.
3. Generate interactive SVG treemap reports for C3, coverage, complexity, and churn.
4. Eliminate Ant and legacy build integration from runtime behavior.
5. Support multiple runtime integrations (Gradle, CLI, and other build systems).
6. Support multi-module repositories and produce one consolidated report set for the full repository.

## 3. Non-Goals
1. Preserve Ant task compatibility.
2. Preserve exact legacy XML formatting/ordering as canonical output.
3. Reproduce historical parser bugs or undefined behavior.
4. Re-implement full coverage/complexity/churn analyzers from scratch.

## 4. Canonical Outputs
The implementation must produce (at minimum):
1. `priospot.json`
2. `priospot-interactive-treemap.svg`
3. `coverage-interactive-treemap.svg`
4. `complexity-interactive-treemap.svg`
5. `churn-interactive-treemap.svg`

Optional outputs:
1. `gitlog.txt`
2. raw tool reports (for debugging only)
3. compatibility `panopticode.xml` exporter (temporary migration mode)

## 5. High-Level Pipeline
Order is mandatory:
1. Build canonical file inventory (and optional source structure model when available).
2. Import churn metrics.
3. Import complexity metrics.
4. Import coverage metrics (from host coverage reports, normalized to JSON).
5. Compute C3 indicator.
6. Emit canonical JSON model.
7. Generate treemap SVG reports from JSON model.

## 6. Core Metric Definitions

### 6.1 Churn Metrics (file level)
1. `Lines Added` (integer)
2. `Lines Removed` (integer)
3. `Times Changed` (integer)
4. `Lines Changed Indicator` (decimal)
5. `Change Frequency Indicator` (decimal)

Project level:
1. `Churn Duration` (integer, days)

### 6.2 Complexity Metrics
File level (required):
1. `NCSS` (integer) or mapped `Logical Statements` equivalent
2. `MAX-CCN` (integer, max cyclomatic complexity in file)

Class/method level (optional enrichment):
1. `NCSS` (integer)
2. `CCN` (integer)

### 6.3 Coverage Metrics
File level (required):
1. `Line Coverage` (ratio)

Class/method level (optional enrichment):
1. `Line Coverage` (ratio)
2. `Branch Coverage` (ratio)
3. `Method Coverage` (ratio)

### 6.4 C3 Metric
File level:
1. `C3 Indicator` (decimal)

## 7. Exact Math (must match)
Given:
1. `numChanges` = number of commits touching file in churn window
2. `linesChanged` = lines added + lines removed in churn window
3. `days` = churn window days (default 30)
4. `maxCCN` = file maximum method CCN
5. `coverage` = file line coverage ratio in [0, 1]

Formulas:
1. `changeFrequencyIndicator = 1 - exp((-2.3025 * numChanges) / days)`
2. `linesChangedIndicator = 1 - exp((-0.05756 * linesChanged) / days)`
3. `maxCCNIndicator = 1 - exp(-0.092103 * maxCCN)`
4. `c3Indicator = ((((linesChangedIndicator + changeFrequencyIndicator) / 2) + maxCCNIndicator + (1 - coverage)) / 3)`

C3 must be computed only if all required file metrics are present.

## 8. Treemap Semantics

### 8.1 Size Rule
Each file rectangle size must be proportional to file `NCSS`.

### 8.2 C3 Color Categories
1. `[0.0, 0.3)` -> green
2. `[0.3, 0.6)` -> yellow
3. `[0.6, 0.9)` -> red
4. `[0.9, 1.0]` -> black fill with red border

### 8.3 Coverage / Complexity / Churn Color Semantics
1. Coverage:
   1. `>= 80%` -> green
   2. `>= 50% and < 80%` -> yellow
   3. `< 50%` -> red
   4. missing coverage metric -> gray
2. Complexity:
   1. gradient from light red to deep red, scaled by `MAX-CCN` and clamped to display max 30
3. Churn:
   1. gradient from light red to deep red, scaled by `Times Changed` and clamped to display max 25

### 8.4 Hierarchy and Layout Semantics
1. Treemap hierarchy must be nested: `module -> package -> sub-package -> file`.
2. Module boundaries must be visually clear and use larger spacing than package/sub-package levels.
3. Spacing must be depth-aware:
   1. module level spacing is highest
   2. top package level spacing is next
   3. deeper levels progressively reduce spacing
4. Spacing must be visual only and must not change NCSS-based area proportionality.

### 8.5 Required Interactive Behavior
SVG must include:
1. Hover tooltip on file cells (file path + primary metric for the report type).
2. Hover tooltip on package/module boundaries.
3. Click-to-select behavior with right-side details panel showing:
   1. selected file path
   2. primary metric for active report
   3. all file metrics
4. Click highlight behavior:
   1. selected file box highlighted
   2. full parent package chain highlighted with varying colors
   3. module-level boundary highlighted with a distinct color
5. In-box text labels are optional and may be omitted when density is high.
6. Right panel must include a checkbox control to include/exclude test classes (`src/test/**`).
7. When test classes are excluded, treemap layout must be re-rendered using only non-test files (not just hidden in-place).

### 8.6 Legend
1. Every generated SVG must include a legend in the right panel.
2. Legend must describe report-type-specific color meaning and thresholds.

## 9. Canonical JSON Schema

## 9.1 Top-Level Shape
```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-02-28T06:21:09Z",
  "project": {
    "name": "priospot",
    "version": "1.0.0",
    "basePath": "/abs/path",
    "metrics": [],
    "files": [],
    "supplements": []
  }
}
```

## 9.2 Entity Types
1. `Project` (required)
2. `File` (required)
3. `Package` (optional)
4. `Class` (optional)
5. `Method` (optional)

## 9.3 Required Entity Fields

### Project
1. `name: string`
2. `version: string|null`
3. `basePath: string`
4. `metrics: Metric[]`
5. `files: File[]`
6. `supplements: SupplementDeclaration[]` (optional)
7. `packages: Package[]` (optional)

### Package
1. `name: string`
2. `metrics: Metric[]`
3. `files: File[]`

### File
1. `name: string`
2. `path: string` (normalized with `/`)
3. `metrics: Metric[]`
4. `classes: Class[]` (optional)

### Class (optional)
1. `name: string`
2. `fullyQualifiedName: string`
3. `position: { line: int, column: int }`
4. `flags: { isAbstract: bool, isInterface: bool, isEnum: bool, isStatic: bool }`
5. `metrics: Metric[]`
6. `methods: Method[]`

### Method (optional)
1. `name: string`
2. `fullyQualifiedName: string`
3. `position: { line: int, column: int }`
4. `isConstructor: bool`
5. `isAbstract: bool`
6. `arguments: Argument[]`
7. `metrics: Metric[]`

### Argument (optional)
1. `name: string`
2. `fullyQualifiedType: string`
3. `simpleType: string`
4. `isParameterizedType: bool`
5. `isVarArg: bool`

## 9.4 Metric Type Union
```json
{
  "kind": "integer",
  "name": "NCSS",
  "value": 12
}
```
```json
{
  "kind": "decimal",
  "name": "C3 Indicator",
  "value": 0.42
}
```
```json
{
  "kind": "ratio",
  "name": "Line Coverage",
  "numerator": 80.0,
  "denominator": 100.0
}
```

All metrics must include `name` and `kind`.

## 9.5 Coverage Normalized JSON Schema (`coverage.json`)

### 9.5.1 Top-Level Shape
```json
{
  "schemaVersion": 1,
  "generator": "coverageReport",
  "generatedAt": "2026-02-28T06:21:09Z",
  "files": []
}
```

### 9.5.2 File Entry
```json
{
  "path": "src/main/<lang>/com/example/Foo.<ext>",
  "lineCoverage": { "covered": 80, "total": 100 },
  "branchCoverage": { "covered": 12, "total": 20 },
  "classes": []
}
```

Required fields:
1. `path: string` (repo-relative preferred, normalized `/`)
2. `lineCoverage.covered: int >= 0`
3. `lineCoverage.total: int >= 0`

Optional fields:
1. `branchCoverage.covered: int >= 0`
2. `branchCoverage.total: int >= 0`
3. `classes: CoverageClass[]`

### 9.5.3 Class Entry (optional)
```json
{
  "name": "com.example.Foo",
  "lineCoverage": { "covered": 50, "total": 60 },
  "branchCoverage": { "covered": 8, "total": 12 },
  "methods": []
}
```

### 9.5.4 Method Entry (optional)
```json
{
  "name": "bar",
  "signature": "bar(<argTypes>):<returnType>",
  "lineCoverage": { "covered": 20, "total": 24 },
  "branchCoverage": { "covered": 4, "total": 6 }
}
```

### 9.5.5 Invariants
1. `covered <= total` for every ratio object.
2. Empty or missing `branchCoverage` is allowed.
3. File list must be sorted lexicographically by `path`.
4. Class list must be sorted by `name`.
5. Method list must be sorted by `signature` (fallback `name`).
6. Unknown fields must be ignored by consumers.

## 10. Input Contracts

### 10.1 Source Model Input
1. Source roots from one or more languages.
2. Source model enrichment is optional; C3 must still run with file-level metrics only.
3. When source model is available, include non-public members and nested/inner constructs where supported.

### 10.2 Churn Input
Mode A (default):
1. Execute git log command equivalent to:
   `git log --all --numstat --date=short --pretty=format:--%h--%ad--%aN --no-renames --relative --since="<days>.days"`

Mode B (baseline):
1. Consume fixed churn log file.
2. Do not execute git.

### 10.3 Coverage Input
1. Primary input is a coverage report produced by the host project's existing test/coverage workflow.
2. Supported examples by ecosystem:
   1. JVM: Kover/JaCoCo XML
   2. Node/TS: LCOV or Cobertura XML
   3. Python: Coverage.py XML
   4. .NET: OpenCover/Cobertura XML
   5. Go/PHP/Rust: adapter-supported coverage exports
3. Coverage XML must be normalized into canonical internal JSON (`coverage.json`) before merge.
4. Map file/class/method counters from normalized JSON to project model.
5. The C3 integration must not require a specific host coverage task name.
6. Multiple coverage reports may be provided; file metrics must be merged by normalized file path.

### 10.3.1 Coverage Normalization Contract
Normalized coverage JSON must contain:
1. `files[]` with `path` (normalized `/` separators)
2. `lineCoverage` ratio (`covered`, `total`)
3. `branchCoverage` ratio (`covered`, `total`) when available
4. optional `classes[]` and `methods[]` entries for fine-grained mapping
5. deterministic ordering by file path
6. schema must conform to section `9.5`

When file-level coverage is missing after merge:
1. non-test source files default to configured fallback (`defaultCoverageNumerator/defaultCoverageDenominator`, default `0/1`)
2. test source files (`src/test/**`) default to full coverage (`1/1`) to avoid penalizing test code for missing self-coverage reports

### 10.4 Complexity Input
1. Primary complexity source is host-generated static-analysis output.
2. Supported examples by ecosystem:
   1. Kotlin/Java: Detekt/PMD-style reports
   2. TypeScript/JavaScript: ESLint complexity metrics
   3. Python: Radon
   4. Go: gocyclo
   5. C#/.NET: Roslyn analyzers
   6. PHP: PHPMD/PDepend
   7. Rust: adapter-supported complexity exports
3. Required mapped metrics remain: file-level `MAX-CCN` and file-level `NCSS` (or mapped `Logical Statements`).
4. If tool output does not provide all required fields directly, use an adapter/fallback metric source instead of writing a full custom analyzer.
5. Tooling may differ, but final mapped metrics must match required names/levels.
6. Multiple complexity reports may be provided; file metrics must be merged by normalized file path.
7. Parsers must support common Detekt XML variants, including checkstyle-style output.
8. When a source file is present in inventory but absent in complexity reports, implementation should derive file-level `MAX-CCN` and `NCSS` directly from source where possible before applying default fallback.

### 10.5 Metric Provider Contract (Reuse First)
1. Coverage must come from host-generated coverage reports.
2. Complexity must come from host-generated static-analysis output.
3. Source-derived complexity fallback is allowed when host complexity output does not contain a file present in source inventory.
4. Churn must come from git history (`git log`/JGit) or fixed baseline churn log.
5. The C3 implementation scope is:
   1. parse/adapt tool outputs
   2. normalize to canonical JSON
   3. compute C3 and generate reports
6. The C3 implementation must not introduce a proprietary full parser for metrics already available from standard tools.
7. Path normalization is mandatory so coverage, complexity, and churn metrics join on the same canonical file path key.

## 11. Execution Contract (Integration-Agnostic)
Primary contract: each host integration must expose one top-level command/task named `priospot`.

### 11.1 Required Entry Point
1. `priospot` (primary lifecycle entry point)

`priospot` must produce all canonical outputs in one invocation.

### 11.2 Configuration Keys
Expose these keys in the host integration configuration:
1. `projectName`
2. `projectVersion`
3. `baseNamespace` (optional)
4. `sourceRoots`
5. `coverageReports` (list of paths to host-generated coverage reports)
6. `complexityReports` (list of paths to host complexity reports)
7. `churnDays` (default `30`)
8. `churnLog` (optional; baseline mode)
9. `outputDir`
10. `emitCompatibilityXml` (default `false`)
11. `deterministicTimestamp` (optional; baseline mode)
12. `coverageTask` (optional task/command name to run before PrioSpot)
13. `complexityTask` (optional task/command name to run before PrioSpot)
14. `defaultCoverageNumerator` (optional, default `0.0`)
15. `defaultCoverageDenominator` (optional, default `1.0`)
16. `defaultMaxCcn` (optional, default `1000`)

Coverage default notes:
1. `defaultCoverageNumerator/defaultCoverageDenominator` apply to non-test files when coverage is missing
2. test files under `src/test/**` are forced to `1/1` when coverage is missing

### 11.3 Wiring Requirements
1. `priospot` must orchestrate analyze + report generation internally.
2. If `coverageTask` is configured, `priospot` must run/depend on it.
3. If `complexityTask` is configured, `priospot` must run/depend on it.
4. If report lists are omitted in Gradle integration, `priospot` should auto-discover existing reports in submodules using default paths:
   1. `build/reports/kover/report.xml`
   2. `build/reports/detekt/detekt.xml`
5. If configured report files are missing, emit warnings and continue with metric defaults.
6. Integration should declare task inputs/outputs for incremental behavior where feasible.

## 12. Optional CLI Contract
CLI is optional and nice-to-have. If implemented, it must mirror core C3 behavior.

Application name: `priospot` (placeholder, configurable)

### 12.1 `analyze`
Build/merge the full model and emit JSON.

Arguments:
1. `--project-name <string>`
2. `--project-version <string?>`
3. `--source-roots <comma-separated paths>`
4. `--coverage-report <path>`
5. `--complexity-report <path>`
6. `--churn-days <int>` (default `30`)
7. `--churn-log <path?>` (if set, skip git execution)
8. `--output-json <path>`
9. CLI currently accepts single coverage/complexity report paths; list support is optional for CLI and required for Gradle integration.

### 12.2 `report`
Generate one report from JSON.

Arguments:
1. `--input-json <path>`
2. `--type <priospot|coverage|complexity|churn>`
3. `--output-svg <path>`
4. `--interactive <true|false>` (default `true`)

## 13. Configuration File Contract
Config keys:
1. `projectName`
2. `projectVersion`
3. `baseNamespace` (optional)
4. `sourceRoots`
5. `coverageReports`
6. `complexityReports`
7. `churnDays`
8. `churnLog` (optional; baseline mode)
9. `outputDir`
10. `emitCompatibilityXml` (default false)
11. `deterministicTimestamp` (optional; baseline mode)
12. `coverageTask` (optional)
13. `complexityTask` (optional)
14. `defaultCoverageNumerator` (optional)
15. `defaultCoverageDenominator` (optional)
16. `defaultMaxCcn` (optional)

## 14. Error Handling
1. Missing required input file -> fail fast with non-zero exit.
2. Missing optional metric source -> emit warning and continue.
3. Missing configured coverage/complexity report file -> warning + continue; unmatched complexity files should use source-derived complexity where possible, else defaults.
4. Method/class mapping miss -> warning in diagnostics section, continue.
5. Invalid formula inputs (division by zero, null metrics) -> skip C3 for that file and warn.

## 15. Observability
1. Structured logs with stage timing.
2. Summary counters:
   1. files parsed
   2. files with churn
   3. files with coverage
   4. files with complexity
   5. files with C3 computed
   6. files with complexity from report
   7. files with complexity from source
   8. files with fallback complexity
3. Warning list for unmapped methods/classes.

## 16. Reference Implementation Profile (Kotlin/Gradle)
1. Kotlin 2.x
2. JDK 17+
3. Gradle Kotlin DSL
4. Modular architecture:
   1. `model`
   2. `ingest-source`
   3. `ingest-churn`
   4. `ingest-coverage`
   5. `ingest-complexity`
   6. `compute-c3`
   7. `report-svg`
   8. `gradle-plugin` (required)
   9. `cli` (optional)

Recommended libs:
1. Jackson (JSON) for canonical serialization
2. Jackson XML or StAX for XML coverage adapters
3. Adapter parsers for tool reports (Detekt, LCOV, Radon, etc.)
4. picocli or Clikt for CLI
5. JGit or process execution for git

### 17.1 Kotlin Reference Example (Non-Normative)
Example `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("io.specmatic.priospot") version "1.0.0"
}

priospot {
    projectName.set("sample-kotlin-service")
    projectVersion.set("1.2.0")
    baseNamespace.set("com.example")
    sourceRoots.set(listOf("src/main/kotlin", "src/test/kotlin"))
    coverageReports.set(listOf("build/reports/kover/report.xml"))
    complexityReports.set(listOf("build/reports/detekt/detekt.xml"))
    coverageTask.set("koverXmlReport")
    complexityTask.set("detekt")
    churnDays.set(30)
    outputDir.set(layout.buildDirectory.dir("reports/priospot"))
}
```

Example execution:
1. `./gradlew priospot`

Expected outputs:
1. `build/reports/priospot/priospot.json`
2. `build/reports/priospot/priospot-interactive-treemap.svg`
3. `build/reports/priospot/coverage-interactive-treemap.svg`
4. `build/reports/priospot/complexity-interactive-treemap.svg`
5. `build/reports/priospot/churn-interactive-treemap.svg`

## 18. Testing Requirements

### 18.1 Unit Tests
1. C3 formula numeric tests (including boundary cases).
2. Indicator function tests with known fixtures.
3. Metric aggregation tests (`MAX-CCN`, file NCSS, file coverage).

### 18.2 Integration Tests
1. End-to-end run on sample project via `priospot`.
2. End-to-end runs on at least two different language ecosystems via `priospot`.
3. Validate report files exist and are parseable SVG.
4. Verify integration wiring for both modes:
   1. with `coverageTask`
   2. without `coverageTask` (pre-generated `coverageReports`)
5. Verify ingestion from host-generated complexity report(s) (or configured `complexityReports` input).

## 19. Migration Compatibility Mode
During transition, provide optional XML export:
1. `--emit-compat-xml` writes `panopticode.xml` derived from JSON model.
2. XML is not canonical; JSON is source of truth.

Compatibility mode can be removed after downstream consumers migrate.

## 20. Acceptance Criteria (Definition of Done)
1. Host integration `priospot` command succeeds on target repositories.
2. C3, coverage, complexity, churn SVGs are generated.
3. `priospot.json` contains all required entities and metric names.
4. C3 values match formula exactly.
5. No Ant runtime dependency in execution path.
6. CI runs analysis and report generation successful.
7. `priospot` consumes host-generated coverage reports and supports optional `coverageTask`.
8. `priospot` ingests host-generated complexity/churn inputs and only computes aggregation + C3/reporting.
9. At least two language adapters are validated end-to-end (for example Kotlin and TypeScript).

## 21. Delivery Artifacts
Implementation must include:
1. Source code and at least one host integration (Gradle required for Kotlin profile).
2. `README.md` with quickstart.
3. JSON schema file (`schema/panopticode.schema.json`).
4. Example Gradle configuration and sample project.
5. One non-Gradle integration example (for example Node.js script wrapper or CLI invocation).
