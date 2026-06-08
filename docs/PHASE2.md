# Phase 2 — Tools, AST Retrieval & First Green Run

**Status:** ✅ Complete — agent fixes one seeded bug end-to-end on local Ollama.
**Acceptance (PROJECT.md §10 / PLAN.md):** the agent autonomously fixes one easy
seeded bug. Verified on `benchmark/projects/calc01` with the default model out of
the box (see [§5 Demo](#5-end-to-end-demo)).

---

## 1. Scope

Phase 2 turned the Phase 1 skeleton (agent loop + read-only file tools) into an
agent that can **act**: write files, run a project's tests, and locate code by
symbol without dumping the whole repo. It also added the first benchmark fixture
to prove the loop closes (red tests → edit → green tests).

| Capability | Delivered |
|---|---|
| File mutation with guardrails | `FileTools.writeFile` / `listFiles` (+ test-write guard) |
| Run project tests | `TestRunner` abstraction, Gradle + Maven implementations |
| AST-aware code search (RAG) | `SymbolIndexer` → `SymbolIndex` → `SymbolRetriever` → `SearchCodeTool` |
| Agent + CLI wiring | all tools registered, bug-fix system prompt |
| First seeded bug | `benchmark/projects/calc01` (off-by-one in `Average.compute`) |

---

## 2. Components

### 2.1 File tools — `agent-core/.../tools/FileTools.java`
- `readFile(path)` — read a file relative to the project root (Phase 1).
- `writeFile(path, content)` — **overwrite** a file with full new content.
- `listFiles(path)` — list immediate children of a directory (`''`/`.` = root).
- **Guardrail:** writes under `**/src/test/**` are blocked — the tests are the
  spec and must not be edited by the agent.
- All paths are resolved and confined to the project root (no escaping via `..`).
- Tests: `FileToolsTest` (18 cases).

### 2.2 Test runner — `agent-core/.../exec/`
- `TestRunner` interface + `TestRunner.forProject(root)` factory that
  **auto-detects** Gradle vs Maven from the project layout.
- `ProcessTestRunner` (abstract base) shells out, streams + truncates output
  (50 KB cap), enforces a 5-minute timeout, and drains the pipe so the child can
  exit.
- `GradleTestRunner` / `MavenTestRunner` — prefer a wrapper (`gradlew`/`mvnw`)
  when present, else fall back to the system binary.
- `JUnitXmlParser` — extracts failing test identifiers from the JUnit XML
  reports (`build/test-results/test`, `target/surefire-reports`).
- `TestResult` record — `passed`, `failingTests`, `outputTail`, `exitCode`,
  `duration`.
- Exposed to the agent via `TestRunnerTool.runTests()`.
- Tests: `TestRunnerFactoryTest` (6), `JUnitXmlParserTest` (4),
  `TestRunnerToolTest` (3).

### 2.3 AST retrieval (RAG) — `agent-core/.../rag/`
- `SymbolIndexer` — walks the project source tree with **JavaParser** and
  records every class / method / field as a `Symbol` (name, kind, FQN, file,
  line range, signature, snippet).
- `SymbolIndex` — in-memory index built once at startup.
- `SymbolRetriever` (implements `Retriever`) — token-based scoring of the query
  against symbol names / signatures; returns the top matches.
- `SearchCodeTool.searchCode(query)` — the agent-facing tool; formats matches as
  `KIND · FQN · file:lines · signature · snippet` so the model can locate code
  without reading whole files.
- Tests: `SymbolIndexerTest` (6), `SymbolRetrieverTest` (6),
  `SearchCodeToolTest` (3).

### 2.4 Agent + CLI wiring — `agent-cli/.../App.java`
- Indexes symbols, registers `FileTools` + `SearchCodeTool` +
  `TestRunnerTool`, and runs the loop with a **bug-fix system prompt** that
  prescribes the workflow: `runTests` → `searchCode`/`readFile` →
  `writeFile` (production only) → re-run `runTests` → stop when green.
- Usage: `./gradlew :agent-cli:run --args="<projectPath> [<prompt>]"`.

---

## 3. Tool surface exposed to the model

| Tool | Purpose |
|---|---|
| `runTests()` | run the project's tests; returns pass/fail + failing names + output tail |
| `searchCode(query)` | locate classes/methods/fields by name (RAG, no full-file dump) |
| `readFile(path)` | read a file relative to the project root |
| `writeFile(path, content)` | overwrite a **production** file (writes under `src/test/` blocked) |
| `listFiles(path)` | list a directory |

---

## 4. Benchmark fixture — `benchmark/projects/calc01`

A standalone Gradle project with a planted **off-by-one** bug:

```java
// Average.compute — buggy
return (double) sum / (xs.length - 1);   // should be xs.length
```

Baseline: **3 of 5 tests fail** (`averageOfThreeNumbers`, `averageOfEvenSpread`,
`averageOfSingleNumber`); the two argument-validation tests pass. The fixture is
kept in its **buggy** state so the harness always starts red.

---

## 5. End-to-end demo

Run on 2026-06-05 against local Ollama, default config (no env override):

```
[cfg] model=qwen2.5:7b
runTests  -> FAILED   (3 failing tests reported by name)
searchCode/readFile   (locates Average.compute)
writeFile -> OK       (full corrected file written)
runTests  -> PASSED   (5/5)
stopReason: COMPLETED  iterations=7
```

The fixture was restored to its buggy state afterward.

---

## 6. Integration issues found & fixed during the demo

The first live run surfaced three real defects (all fixed; build + 51 unit tests
green). These are the kind of issue only an end-to-end run on the target OS
exposes.

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | Agent "completes" on iteration 1 without acting | `qwen2.5-coder:7b` returns tool calls as **plain text**, not structured `tool_calls` (`tool_calls: null` even at raw Ollama) | Default model → **`qwen2.5:7b` (instruct)** in `AgentConfig.defaults()` + docs. Never use a `coder` variant for this agent. |
| 2 | `runTests` always errors `CreateProcess error=2` on Windows | Java `ProcessBuilder` does not apply `PATHEXT`, so bare `"gradle"`/`"mvn"` can't launch | `GradleTestRunner`/`MavenTestRunner` use `gradle.bat`/`mvn.cmd` on Windows |
| 3 | `writeFile` crashes with `JsonParseException` on large content | `langchain4j-ollama:1.0.0-beta5` mis-serializes tool-call args (inner quotes unescaped); confirmed not the model (6/6 raw Ollama calls were valid) | Bump `langchain4j` + `langchain4j-ollama` to **1.2.0** (first stable ollama) in `gradle.properties` |

---

## 7. Test inventory (agent-core)

51 `@Test` methods across 9 suites — all passing.

| Suite | Tests |
|---|---|
| `FileToolsTest` | 18 |
| `TestRunnerFactoryTest` | 6 |
| `SymbolIndexerTest` | 6 |
| `SymbolRetrieverTest` | 6 |
| `JUnitXmlParserTest` | 4 |
| `AgentTest` | 4 |
| `SearchCodeToolTest` | 3 |
| `TestRunnerToolTest` | 3 |
| `AgentConfigTest` | 1 |

---

## 8. Commands

```bash
# Build + all unit tests
./gradlew build

# Run the agent on the seeded bug (default model qwen2.5:7b)
./gradlew :agent-cli:run --args="<abs-path>/benchmark/projects/calc01"

# Inspect the fixture's baseline (3 fail, 2 pass)
gradle -p benchmark/projects/calc01 test
```

See [docs/RUN.md](RUN.md) for the full local setup (Ollama install, model pull,
expected trace, troubleshooting).

---

## 9. Next — Phase 3

Benchmark + harness: an `EvalHarness` that runs the agent over a set of seeded
bugs and reports a pass rate, building on the `calc01` fixture established here.
